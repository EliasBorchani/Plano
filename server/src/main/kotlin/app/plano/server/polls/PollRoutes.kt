package app.plano.server.polls

import app.plano.contract.AddDateOptionRequest
import app.plano.contract.DatePollDto
import app.plano.contract.PollOptionDto
import app.plano.contract.Routes
import app.plano.contract.VoteDto
import app.plano.contract.VoteRequest
import app.plano.domain.polls.model.VoteChoice
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.auth.Sessions
import app.plano.server.auth.requireUser
import app.plano.server.events.requireRole
import app.plano.server.forbidden
import app.plano.server.invalid
import app.plano.server.notFound
import app.plano.server.query
import app.plano.server.toUuidOr404
import app.plano.server.update
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.sql.Connection
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private fun Connection.datePollId(eventId: UUID): UUID =
    query("select id from poll where event_id = ? and kind = 'DATE'", eventId) { it.getObject(1, UUID::class.java) }.single()

private fun Connection.loadPoll(pollId: UUID): DatePollDto = DatePollDto(
    id = pollId.toString(),
    options = query("select id, date from poll_option where poll_id = ? order by date", pollId) {
        PollOptionDto(id = it.getString(1), date = it.getObject(2, LocalDate::class.java).toString())
    },
    votes = query(
        "select v.option_id, v.user_id, v.choice from vote v join poll_option o on o.id = v.option_id where o.poll_id = ?",
        pollId,
    ) { VoteDto(optionId = it.getString(1), userId = it.getString(2), choice = it.getString(3)) },
)

fun Route.pollRoutes(db: Db, sessions: Sessions) {
    get(Routes.poll("{eventId}")) {
        val user = call.requireUser(sessions)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val poll = db.tx {
            requireRole(eventId, user.id)
            loadPoll(datePollId(eventId))
        }
        call.respond(poll)
    }

    post(Routes.pollOptions("{eventId}")) {
        val user = call.requireUser(sessions)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val body = call.receive<AddDateOptionRequest>()
        val date = runCatching { LocalDate.parse(body.date) }.getOrNull() ?: invalid("DATE_INVALID")
        // A day of slack: the client checks against its own time zone, which may be ahead of UTC.
        if (date < LocalDate.now(ZoneOffset.UTC).minusDays(1)) invalid("DATE_IN_PAST")
        val poll = db.tx {
            if (!requireRole(eventId, user.id).canPlan) forbidden()
            val pollId = datePollId(eventId)
            val added = update(
                "insert into poll_option (poll_id, date, created_by) values (?, ?, ?) on conflict do nothing",
                pollId,
                date,
                user.id,
            )
            if (added == 0) throw ApiError(HttpStatusCode.Conflict, "DATE_ALREADY_OFFERED")
            loadPoll(pollId)
        }
        call.respond(poll)
    }

    put(Routes.vote("{eventId}", "{optionId}")) {
        val user = call.requireUser(sessions)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val optionId = call.parameters["optionId"].toUuidOr404()
        val body = call.receive<VoteRequest>()
        val choice = body.choice?.let { VoteChoice.fromName(it) ?: invalid("choice_unknown") }
        val poll = db.tx {
            requireRole(eventId, user.id)
            val pollId = datePollId(eventId)
            if (query("select 1 from poll_option where id = ? and poll_id = ?", optionId, pollId) { 1 }.isEmpty()) notFound()
            if (choice == null) {
                update("delete from vote where option_id = ? and user_id = ?", optionId, user.id)
            } else {
                update(
                    "insert into vote (option_id, user_id, choice) values (?, ?, ?) " +
                        "on conflict (option_id, user_id) do update set choice = excluded.choice, updated_at = now()",
                    optionId,
                    user.id,
                    choice.name,
                )
            }
            loadPoll(pollId)
        }
        call.respond(poll)
    }
}
