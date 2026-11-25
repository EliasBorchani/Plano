package app.plano.server.events

import app.plano.contract.CreateEventRequest
import app.plano.contract.CreatedDto
import app.plano.contract.EventDto
import app.plano.contract.EventSummaryDto
import app.plano.contract.MemberDto
import app.plano.contract.Routes
import app.plano.domain.events.model.EventType
import app.plano.domain.events.model.NewEvent
import app.plano.domain.events.model.Role
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.auth.Auth
import app.plano.server.auth.requireUser
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
import java.sql.Connection
import java.util.UUID

/** The caller's role in the event, or 404: a non-member must not learn the event exists. */
fun Connection.requireRole(eventId: UUID, userId: UUID): Role =
    query("select role from event_member where event_id = ? and user_id = ?", eventId, userId) {
        Role.fromName(it.getString(1))
    }.firstOrNull() ?: notFound()

/** The event a guest belongs to, or null for an account. */
private fun Connection.guestEventOf(userId: UUID): UUID? =
    query("select guest_event_id from app_user where id = ?", userId) { it.getObject(1, UUID::class.java) }.firstOrNull()

fun Route.eventRoutes(db: Db, auth: Auth) {
    get(Routes.EVENTS) {
        val user = call.requireUser(auth)
        val events = db.tx {
            query(
                "select e.id, e.title, e.type, m.role from event_member m join event e on e.id = m.event_id " +
                    "where m.user_id = ? order by e.created_at desc",
                user.userId,
            ) { EventSummaryDto(it.getString(1), it.getString(2), it.getString(3), it.getString(4)) }
        }
        call.respond(events)
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
            update("insert into event_member (event_id, user_id, role) values (?, ?, ?)", id, user.userId, Role.OWNER.name)
            update("insert into poll (event_id, kind) values (?, 'DATE')", id)
            id
        }
        call.respond(HttpStatusCode.Created, CreatedDto(id.toString()))
    }

    get(Routes.event("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        val dto = db.tx {
            val role = requireRole(eventId, user.userId)
            val members = query(
                "select u.id, u.display_name, m.role from event_member m join app_user u on u.id = m.user_id " +
                    "where m.event_id = ? order by m.joined_at",
                eventId,
            ) { MemberDto(it.getString(1), it.getString(2), it.getString(3)) }
            query("select id, title, type, description from event where id = ?", eventId) {
                EventDto(
                    id = it.getString(1),
                    title = it.getString(2),
                    type = it.getString(3),
                    description = it.getString(4),
                    myRole = role.name,
                    members = members,
                )
            }.single()
        }
        call.respond(dto)
    }

    // Slice rule: any account that has the link can join. Invitations come later.
    // A guest belongs to one event (see V3__guests.sql) and joins it when created.
    post(Routes.join("{eventId}")) {
        val user = call.requireUser(auth)
        val eventId = call.parameters["eventId"].toUuidOr404()
        db.tx {
            if (query("select 1 from event where id = ?", eventId) { 1 }.isEmpty()) notFound()
            guestEventOf(user.userId)?.let { if (it != eventId) throw ApiError(HttpStatusCode.Forbidden, "GUEST_OF_OTHER_EVENT") }
            update(
                "insert into event_member (event_id, user_id, role) values (?, ?, ?) on conflict do nothing",
                eventId,
                user.userId,
                Role.DEFAULT_FOR_NEW_MEMBER.name,
            )
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
