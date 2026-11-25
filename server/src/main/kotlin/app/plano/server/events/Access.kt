package app.plano.server.events

import app.plano.domain.events.model.MemberStatus
import app.plano.domain.events.model.Role
import app.plano.server.ApiError
import app.plano.server.notFound
import app.plano.server.query
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.util.UUID

data class Membership(
    val role: Role,
    val status: MemberStatus,
    val archived: Boolean,
)

/** The caller's place in the event, or 404: a non-member must not learn the event exists. */
fun Connection.requireMembership(eventId: UUID, userId: UUID): Membership =
    query(
        "select m.role, m.status, e.archived_at is not null from event_member m join event e on e.id = m.event_id " +
            "where m.event_id = ? and m.user_id = ?",
        eventId,
        userId,
    ) {
        Membership(Role.fromName(it.getString(1))!!, MemberStatus.fromName(it.getString(2))!!, it.getBoolean(3))
    }.firstOrNull() ?: notFound()

/** The caller's role in the event; 403 `MEMBER_LEFT` for someone who left or was removed (title and type only). */
fun Connection.requireRole(eventId: UUID, userId: UUID): Role = requireActive(eventId, userId).role

/** As [requireRole], for a change: 409 `EVENT_ARCHIVED` once the owner archived the event. */
fun Connection.requireEditable(eventId: UUID, userId: UUID): Role {
    val membership = requireActive(eventId, userId)
    if (membership.archived) throw ApiError(HttpStatusCode.Conflict, "EVENT_ARCHIVED")
    return membership.role
}

private fun Connection.requireActive(eventId: UUID, userId: UUID): Membership =
    requireMembership(eventId, userId).also {
        if (!it.status.isActive) throw ApiError(HttpStatusCode.Forbidden, "MEMBER_LEFT")
    }

/** The event a guest belongs to, or null for an account. */
fun Connection.guestEventOf(userId: UUID): UUID? =
    query("select guest_event_id from app_user where id = ?", userId) { it.getObject(1, UUID::class.java) }.firstOrNull()
