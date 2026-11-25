package app.plano.server.polls

import app.plano.contract.AddDateOptionRequest
import app.plano.contract.AddOptionRequest
import app.plano.contract.DatePollDto
import app.plano.contract.DeadlineRequest
import app.plano.contract.LinkOptionDto
import app.plano.contract.OptionDto
import app.plano.contract.PollDto
import app.plano.contract.PollOptionDto
import app.plano.contract.Routes
import app.plano.contract.ValidateRequest
import app.plano.contract.VoteDto
import app.plano.contract.VoteRequest
import app.plano.domain.events.model.Rsvp
import app.plano.domain.links.LinkError
import app.plano.domain.links.Links
import app.plano.domain.polls.model.PollKind
import app.plano.domain.polls.model.PollRules
import app.plano.domain.polls.model.VoteChoice
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.auth.Auth
import app.plano.server.auth.requireUser
import app.plano.server.events.requireEditable
import app.plano.server.events.requireRole
import app.plano.server.forbidden
import app.plano.server.instant
import app.plano.server.invalid
import app.plano.server.notFound
import app.plano.server.now
import app.plano.server.places.placeColumns
import app.plano.server.places.toPlaceDto
import app.plano.server.query
import app.plano.server.toSql
import app.plano.server.toUuidOr404
import app.plano.server.update
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.sql.Connection
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Instant

private const val LINK_TITLE_MAX = 120

private data class PollRow(
    val id: UUID,
    val kind: PollKind,
    val startedAt: Instant?,
    val deadline: Instant?,
    val validatedOptionId: UUID?,
)

private fun Connection.pollOf(eventId: UUID, kind: PollKind): PollRow = query(
    "select id, started_at, deadline, validated_option_id from poll where event_id = ? and kind = ?",
    eventId,
    kind.name,
) { PollRow(it.getObject(1, UUID::class.java), kind, it.instant(2), it.instant(3), it.getObject(4, UUID::class.java)) }
    .single()

private fun Connection.loadPoll(poll: PollRow, now: Instant): PollDto = PollDto(
    id = poll.id.toString(),
    kind = poll.kind.name,
    options = query(
        "select o.id, o.date, o.link_url, o.link_title, ${placeColumns("p")} from poll_option o " +
            "left join place p on p.id = o.place_id where o.poll_id = ? order by o.date, o.created_at",
        poll.id,
    ) {
        OptionDto(
            id = it.getString(1),
            date = it.getObject(2, LocalDate::class.java)?.toString(),
            link = it.getString(3)?.let { url -> LinkOptionDto(url, it.getString(4)) },
            place = it.getString(5)?.let { _ -> it.toPlaceDto(from = 5) },
        )
    },
    votes = query(
        "select v.option_id, v.user_id, v.choice from vote v join poll_option o on o.id = v.option_id where o.poll_id = ?",
        poll.id,
    ) { VoteDto(optionId = it.getString(1), userId = it.getString(2), choice = it.getString(3)) },
    deadline = poll.deadline?.toString(),
    validatedOptionId = poll.validatedOptionId?.toString(),
    isOpen = PollRules.isOpen(now, poll.deadline, poll.validatedOptionId != null),
)

/** The first web client knows only the date poll at `events/{id}/poll`. */
private fun PollDto.asDatePoll() = DatePollDto(
    id = id,
    options = options.map { PollOptionDto(it.id, it.date!!) },
    votes = votes,
    deadline = deadline,
    validatedOptionId = validatedOptionId,
    isOpen = isOpen,
)

private fun RoutingCall.kind(): PollKind =
    parameters["kind"]?.uppercase()?.let(PollKind::fromName) ?: notFound()

private fun Connection.requireOpen(poll: PollRow, now: Instant) {
    if (!PollRules.isOpen(now, poll.deadline, poll.validatedOptionId != null)) {
        throw ApiError(HttpStatusCode.Conflict, "POLL_CLOSED")
    }
}

/** Rebuilds the reminders not sent yet; past ones are skipped (`PollRules.schedule`). */
private fun Connection.reschedule(pollId: UUID, startedAt: Instant, deadline: Instant, now: Instant) {
    val schedule = PollRules.schedule(startedAt, deadline, now)
    update(
        "delete from poll_reminder where poll_id = ? and sent_at is null and not (kind = any (?))",
        pollId,
        createArrayOf("text", schedule.map { it.reminder.name }.toTypedArray()),
    )
    schedule.forEach {
        update(
            "insert into poll_reminder (poll_id, kind, due_at) values (?, ?, ?) on conflict (poll_id, kind) " +
                "do update set due_at = excluded.due_at where poll_reminder.sent_at is null",
            pollId,
            it.reminder.name,
            it.dueAt.toSql(),
        )
    }
}

private class PollService(private val db: Db, private val clock: Clock) {
    suspend fun get(eventId: UUID, userId: UUID, kind: PollKind): PollDto = db.tx {
        requireRole(eventId, userId)
        loadPoll(pollOf(eventId, kind), clock.now())
    }

    suspend fun addOption(eventId: UUID, userId: UUID, kind: PollKind, body: AddOptionRequest): PollDto {
        val now = clock.now()
        val values = listOfNotNull(body.date, body.placeId, body.link)
        if (values.size != 1) invalid("OPTION_INVALID")
        val date = body.date?.let {
            if (kind != PollKind.DATE) invalid("OPTION_INVALID")
            val date = runCatching { LocalDate.parse(it) }.getOrNull() ?: invalid("DATE_INVALID")
            // A day of slack: the client checks against its own time zone, which may be ahead of UTC.
            if (date < LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1)) invalid("DATE_IN_PAST")
            date
        }
        val placeId = body.placeId?.let {
            if (kind != PollKind.PLACE) invalid("OPTION_INVALID")
            runCatching { UUID.fromString(it) }.getOrNull() ?: invalid("PLACE_UNKNOWN")
        }
        val link = body.link?.let {
            if (kind != PollKind.PLACE) invalid("OPTION_INVALID")
            val card = Links.card(it.url) ?: invalid(LinkError.NOT_A_LINK.name)
            val title = it.title.trim().takeIf { t -> t.isNotEmpty() } ?: invalid("LINK_TITLE_MISSING")
            if (title.length > LINK_TITLE_MAX) invalid("LINK_TITLE_TOO_LONG")
            LinkOptionDto(card.canonicalUrl, title)
        }
        return db.tx {
            if (!requireEditable(eventId, userId).canPlan) forbidden()
            var poll = pollOf(eventId, kind)
            requireOpen(poll, now)
            if (placeId != null && query("select 1 from place where id = ?", placeId) { 1 }.isEmpty()) invalid("PLACE_UNKNOWN")
            val added = update(
                "insert into poll_option (poll_id, date, place_id, link_url, link_title, created_by) " +
                    "values (?, ?, ?, ?, ?, ?) on conflict do nothing",
                poll.id,
                date,
                placeId,
                link?.url,
                link?.title,
                userId,
            )
            if (added == 0) throw ApiError(HttpStatusCode.Conflict, if (date != null) "DATE_ALREADY_OFFERED" else "OPTION_ALREADY_OFFERED")
            // The first option starts the poll, with the default deadline (owner, 2026-11-25).
            if (poll.startedAt == null) {
                val deadline = now + PollRules.DEFAULT_DEADLINE
                update("update poll set started_at = ?, deadline = ? where id = ?", now.toSql(), deadline.toSql(), poll.id)
                reschedule(poll.id, now, deadline, now)
                poll = poll.copy(startedAt = now, deadline = deadline)
            }
            loadPoll(poll, now)
        }
    }

    suspend fun vote(eventId: UUID, userId: UUID, kind: PollKind, optionId: UUID, choice: VoteChoice?): PollDto = db.tx {
        val now = clock.now()
        requireEditable(eventId, userId)
        val poll = pollOf(eventId, kind)
        if (query("select 1 from poll_option where id = ? and poll_id = ?", optionId, poll.id) { 1 }.isEmpty()) notFound()
        requireOpen(poll, now)
        if (choice == null) {
            update("delete from vote where option_id = ? and user_id = ?", optionId, userId)
        } else {
            update(
                "insert into vote (option_id, user_id, choice) values (?, ?, ?) " +
                    "on conflict (option_id, user_id) do update set choice = excluded.choice, updated_at = now()",
                optionId,
                userId,
                choice.name,
            )
        }
        loadPoll(poll, now)
    }

    /** Planners move the deadline, earlier or later; this also reopens a poll whose deadline passed. */
    suspend fun setDeadline(eventId: UUID, userId: UUID, kind: PollKind, deadline: Instant): PollDto = db.tx {
        val now = clock.now()
        if (!requireEditable(eventId, userId).canPlan) forbidden()
        val poll = pollOf(eventId, kind)
        val startedAt = poll.startedAt ?: throw ApiError(HttpStatusCode.Conflict, "POLL_NOT_STARTED")
        if (!PollRules.canSetDeadline(now, deadline)) invalid("DEADLINE_TOO_SOON")
        update("update poll set deadline = ? where id = ?", deadline.toSql(), poll.id)
        if (poll.validatedOptionId == null) reschedule(poll.id, startedAt, deadline, now)
        loadPoll(poll.copy(deadline = deadline), now)
    }

    /**
     * A planner picks the winning option (before or after the deadline, ties included) or cancels the choice.
     * A changed choice resets every RSVP (PRD, RSVP).
     */
    suspend fun validate(eventId: UUID, userId: UUID, kind: PollKind, optionId: UUID?): PollDto = db.tx {
        val now = clock.now()
        if (!requireEditable(eventId, userId).canPlan) forbidden()
        val poll = pollOf(eventId, kind)
        if (optionId != null && query("select 1 from poll_option where id = ? and poll_id = ?", optionId, poll.id) { 1 }.isEmpty()) {
            notFound()
        }
        update("update poll set validated_option_id = ? where id = ?", optionId, poll.id)
        if (Rsvp.resetsOnChange(poll.validatedOptionId?.toString(), optionId?.toString())) {
            update("update event_member set rsvp = 'NOT_ANSWERED' where event_id = ?", eventId)
        }
        when {
            optionId != null -> update("delete from poll_reminder where poll_id = ? and sent_at is null", poll.id)
            poll.startedAt != null && poll.deadline != null -> reschedule(poll.id, poll.startedAt, poll.deadline, now)
        }
        loadPoll(poll.copy(validatedOptionId = optionId), now)
    }
}

fun Route.pollRoutes(db: Db, auth: Auth, clock: Clock) {
    val polls = PollService(db, clock)

    get(Routes.poll("{eventId}")) {
        val user = call.requireUser(auth)
        call.respond(polls.get(call.parameters["eventId"].toUuidOr404(), user.userId, PollKind.DATE).asDatePoll())
    }

    post(Routes.pollOptions("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val body = call.receive<AddDateOptionRequest>()
        call.respond(polls.addOption(eventId, user.userId, PollKind.DATE, AddOptionRequest(date = body.date)).asDatePoll())
    }

    put(Routes.vote("{eventId}", "{optionId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val optionId = call.parameters["optionId"].toUuidOr404()
        val choice = call.receive<VoteRequest>().choice?.let { VoteChoice.fromName(it) ?: invalid("choice_unknown") }
        call.respond(polls.vote(eventId, user.userId, PollKind.DATE, optionId, choice).asDatePoll())
    }

    get(Routes.pollOf("{eventId}", "{kind}")) {
        val user = call.requireUser(auth)
        call.respond(polls.get(call.parameters["eventId"].toUuidOr404(), user.userId, call.kind()))
    }

    post(Routes.pollOptionsOf("{eventId}", "{kind}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val kind = call.kind()
        call.respond(polls.addOption(eventId, user.userId, kind, call.receive<AddOptionRequest>()))
    }

    put(Routes.voteOf("{eventId}", "{kind}", "{optionId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val kind = call.kind()
        val optionId = call.parameters["optionId"].toUuidOr404()
        val choice = call.receive<VoteRequest>().choice?.let { VoteChoice.fromName(it) ?: invalid("choice_unknown") }
        call.respond(polls.vote(eventId, user.userId, kind, optionId, choice))
    }

    put(Routes.deadline("{eventId}", "{kind}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val kind = call.kind()
        val deadline = runCatching { Instant.parse(call.receive<DeadlineRequest>().deadline) }.getOrNull()
            ?: invalid("DEADLINE_INVALID")
        call.respond(polls.setDeadline(eventId, user.userId, kind, deadline))
    }

    put(Routes.validated("{eventId}", "{kind}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val kind = call.kind()
        val optionId = call.receive<ValidateRequest>().optionId?.let { it.toUuidOr404() }
        call.respond(polls.validate(eventId, user.userId, kind, optionId))
    }
}
