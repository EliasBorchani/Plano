package app.plano.server.events

import app.plano.contract.ArchiveRequest
import app.plano.contract.CreateEventRequest
import app.plano.contract.CreatedDto
import app.plano.contract.EventDto
import app.plano.contract.EventSummaryDto
import app.plano.contract.GroupSummaryDto
import app.plano.contract.InvitationsDto
import app.plano.contract.InviteesRequest
import app.plano.contract.MemberDto
import app.plano.contract.Routes
import app.plano.contract.RsvpRequest
import app.plano.domain.events.model.EventType
import app.plano.domain.events.model.MemberStatus
import app.plano.domain.events.model.NewEvent
import app.plano.domain.events.model.Role
import app.plano.domain.events.model.Rsvp
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.auth.Auth
import app.plano.server.auth.requireUser
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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.sql.Connection
import java.util.UUID

/** Summaries of the events [userId] is a member of, newest first, optionally only those picked from [groupId]. */
fun Connection.eventSummaries(userId: UUID, groupId: UUID? = null): List<EventSummaryDto> = query(
    "select e.id, e.title, e.type, m.role, m.status, e.archived_at is not null " +
        "from event_member m join event e on e.id = m.event_id where m.user_id = ? " +
        (if (groupId != null) "and exists (select 1 from event_group g where g.event_id = e.id and g.group_id = ?) " else "") +
        "order by e.created_at desc",
    *listOfNotNull(userId, groupId).toTypedArray(),
) { EventSummaryDto(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getBoolean(6)) }

fun Route.eventRoutes(db: Db, auth: Auth) {
    get(Routes.EVENTS) {
        val user = call.requireUser(auth)
        call.respond(db.tx { eventSummaries(user.userId) })
    }

    post(Routes.EVENTS) {
        val user = call.requireUser(auth)
        // A guest lives only in one browser: an event they own could be lost with it.
        if (db.tx { guestEventOf(user.userId) } != null) throw ApiError(HttpStatusCode.Forbidden, "GUEST_CANNOT_CREATE")
        val body = call.receive<CreateEventRequest>()
        val type = EventType.fromName(body.type) ?: invalid("type_unknown")
        // Same rules as the clients run before submitting.
        val event = NewEvent(title = body.title, type = type, description = body.description).normalized()
        event.validate().firstOrNull()?.let { invalid(it.name) }

        val id = db.tx {
            val id = query(
                "insert into event (title, type, description, owner_id) values (?, ?, ?, ?) returning id",
                event.title,
                event.type.name,
                event.description,
                user.userId,
            ) { it.getObject(1, UUID::class.java) }.single()
            update(
                "insert into event_member (event_id, user_id, role, status) values (?, ?, ?, 'JOINED')",
                id,
                user.userId,
                Role.OWNER.name,
            )
            update("insert into poll (event_id, kind) values (?, 'DATE'), (?, 'PLACE')", id, id)
            invite(id, user.userId, body.invitees)
            id
        }
        call.respond(HttpStatusCode.Created, CreatedDto(id.toString()))
    }

    get(Routes.event("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val dto = db.tx {
            val membership = requireMembership(eventId, user.userId)
            // Opening the event is what turns an invitation into « joined ».
            val status = if (membership.status == MemberStatus.INVITED) {
                update("update event_member set status = 'JOINED' where event_id = ? and user_id = ?", eventId, user.userId)
                MemberStatus.JOINED
            } else {
                membership.status
            }
            val left = status == MemberStatus.LEFT
            query("select id, title, type, description, archived_at is not null from event where id = ?", eventId) {
                EventDto(
                    id = it.getString(1),
                    title = it.getString(2),
                    type = it.getString(3),
                    // PRD: someone who left keeps seeing the title and type, nothing else.
                    description = if (left) null else it.getString(4),
                    myRole = membership.role.name,
                    members = emptyList(),
                    myStatus = status.name,
                    archived = it.getBoolean(5),
                )
            }.single().let { if (left) it else it.copy(members = members(eventId), groups = groups(eventId)) }
        }
        call.respond(dto)
    }

    // Anyone signed in with the event link joins as planner (friend groups). A guest belongs to one event (see
    // V3__guests.sql) and joins it when created. Someone who left or was removed needs a planner to come back.
    post(Routes.join("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        db.tx {
            val archived = query("select archived_at is not null from event where id = ?", eventId) { it.getBoolean(1) }
                .firstOrNull() ?: notFound()
            guestEventOf(user.userId)?.let { if (it != eventId) throw ApiError(HttpStatusCode.Forbidden, "GUEST_OF_OTHER_EVENT") }
            val status = query("select status from event_member where event_id = ? and user_id = ?", eventId, user.userId) {
                MemberStatus.fromName(it.getString(1))
            }.firstOrNull()
            when {
                status == MemberStatus.LEFT -> throw ApiError(HttpStatusCode.Forbidden, "MEMBER_LEFT")
                status != null -> update(
                    "update event_member set status = 'JOINED' where event_id = ? and user_id = ? and status = 'INVITED'",
                    eventId,
                    user.userId,
                )
                archived -> throw ApiError(HttpStatusCode.Conflict, "EVENT_ARCHIVED")
                else -> update(
                    "insert into event_member (event_id, user_id, role, status) values (?, ?, ?, 'JOINED')",
                    eventId,
                    user.userId,
                    Role.DEFAULT_FOR_NEW_MEMBER.name,
                )
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    post(Routes.invitations("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val body = call.receive<InviteesRequest>()
        val result = db.tx {
            if (!requireEditable(eventId, user.userId).canPlan) forbidden()
            invite(eventId, user.userId, body)
        }
        call.respond(result)
    }

    get(Routes.invitations("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val links = db.tx {
            if (!requireRole(eventId, user.userId).canPlan) forbidden()
            invitationLinks(eventId)
        }
        call.respond(InvitationsDto(links))
    }

    // Leaving (your own id) or removing someone (planners). Both end as LEFT: the others see a « left » label.
    delete(Routes.member("{eventId}", "{userId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val target = call.parameters["userId"].toUuidOr404()
        db.tx {
            val role = requireRole(eventId, user.userId)
            if (target != user.userId && !role.canPlan) forbidden()
            val targetRole = query(
                "select role from event_member where event_id = ? and user_id = ? and status <> 'LEFT'",
                eventId,
                target,
            ) { Role.fromName(it.getString(1)) }.firstOrNull() ?: notFound()
            if (targetRole == Role.OWNER) {
                throw ApiError(if (target == user.userId) HttpStatusCode.Conflict else HttpStatusCode.Forbidden, "OWNER_CANNOT_LEAVE")
            }
            update(
                "update event_member set status = 'LEFT', rsvp = 'NOT_ANSWERED' where event_id = ? and user_id = ?",
                eventId,
                target,
            )
            // Their votes no longer count; the options they added stay.
            update(
                "delete from vote v using poll_option o, poll p where v.option_id = o.id and o.poll_id = p.id " +
                    "and p.event_id = ? and v.user_id = ?",
                eventId,
                target,
            )
        }
        call.respond(HttpStatusCode.NoContent)
    }

    put(Routes.rsvp("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val answer = Rsvp.fromName(call.receive<RsvpRequest>().answer) ?: invalid("rsvp_unknown")
        db.tx {
            requireEditable(eventId, user.userId)
            update("update event_member set rsvp = ? where event_id = ? and user_id = ?", answer.name, eventId, user.userId)
        }
        call.respond(HttpStatusCode.NoContent)
    }

    // PRD: archiving by the owner locks editing. Unarchiving gives the owner a way back from a mistake.
    put(Routes.archived("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val archived = call.receive<ArchiveRequest>().archived
        db.tx {
            if (requireRole(eventId, user.userId) != Role.OWNER) forbidden()
            update(
                "update event set archived_at = case when ? then coalesce(archived_at, now()) end where id = ?",
                archived,
                eventId,
            )
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Connection.members(eventId: UUID): List<MemberDto> = query(
    "select u.id, u.display_name, m.role, m.status, m.rsvp, u.guest_event_id is not null " +
        "from event_member m join app_user u on u.id = m.user_id where m.event_id = ? order by m.joined_at",
    eventId,
) { MemberDto(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getBoolean(6)) }

private fun Connection.groups(eventId: UUID): List<GroupSummaryDto> = query(
    "select g.id, g.name, (select count(*) from group_member gm where gm.group_id = g.id) " +
        "from event_group eg join user_group g on g.id = eg.group_id where eg.event_id = ? order by g.name",
    eventId,
) { GroupSummaryDto(it.getString(1), it.getString(2), it.getInt(3)) }
