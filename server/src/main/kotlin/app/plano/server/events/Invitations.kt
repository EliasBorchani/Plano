package app.plano.server.events

import app.plano.contract.InvitationLinkDto
import app.plano.contract.InvitationsDto
import app.plano.contract.InviteesRequest
import app.plano.domain.auth.model.GuestName
import app.plano.domain.events.model.EmailAddress
import app.plano.domain.events.model.GroupPick
import app.plano.domain.events.model.Invitees
import app.plano.domain.events.model.MemberStatus
import app.plano.domain.events.model.Role
import app.plano.server.invalid
import app.plano.server.newLinkToken
import app.plano.server.query
import app.plano.server.update
import java.sql.Connection
import java.util.UUID

/**
 * Adds the people of one invite step to [eventId] as `INVITED` (people who left come back as `INVITED`), and
 * remembers the groups they were picked from. People invited by email who have no account become guests of the
 * event with a personal link.
 */
fun Connection.invite(eventId: UUID, inviter: UUID, request: InviteesRequest): InvitationsDto {
    val individuals = request.userIds.map { id ->
        id.toUuidOrNull()?.takeIf { canInvite(inviter, it) }?.toString() ?: invalid("INVITEE_UNKNOWN")
    }
    val picks = request.groups.map { pick ->
        val groupId = pick.groupId.toUuidOrNull()?.takeIf { isGroupMember(it, inviter) } ?: invalid("GROUP_UNKNOWN")
        val members = query("select user_id from group_member where group_id = ?", groupId) { it.getString(1) }
        GroupPick(groupId.toString(), members, pick.untickedUserIds.toSet())
    }
    val emails = request.emails.map { invite ->
        if (!EmailAddress.isValid(invite.email)) invalid("EMAIL_INVALID")
        val name = invite.displayName?.let { raw -> GuestName.validate(raw)?.let { invalid(it.name) }; GuestName.normalize(raw) }
        EmailAddress.normalize(invite.email) to (name ?: EmailAddress.defaultName(invite.email))
    }.distinctBy { it.first }

    val active = query("select user_id, status from event_member where event_id = ?", eventId) {
        it.getString(1) to MemberStatus.fromName(it.getString(2))!!
    }.filter { it.second.isActive }.map { it.first }.toSet()

    val accountsByEmail = emails.mapNotNull { (email, _) -> accountWithEmail(email)?.let { email to it } }.toMap()
    val userIds = Invitees.resolve(
        individuals = individuals + accountsByEmail.values.map(UUID::toString),
        groups = picks,
        inviter = inviter.toString(),
        alreadyMembers = active,
    )
    userIds.forEach { addMember(eventId, UUID.fromString(it), inviter) }
    picks.forEach {
        update("insert into event_group (event_id, group_id) values (?, ?) on conflict do nothing", eventId, UUID.fromString(it.groupId))
    }

    val links = emails.filter { it.first !in accountsByEmail }.map { (email, name) -> emailInvitation(eventId, inviter, email, name) }
    return InvitationsDto(links)
}

/** Planners see the personal links of everyone invited by email, to send them by hand until emails go out. */
fun Connection.invitationLinks(eventId: UUID): List<InvitationLinkDto> = query(
    "select i.email, i.user_id, i.token from event_invitation i join app_user u on u.id = i.user_id " +
        "where i.event_id = ? and u.guest_event_id = i.event_id order by i.created_at",
    eventId,
) { InvitationLinkDto(it.getString(1), it.getString(2), it.getString(3)) }

private fun Connection.addMember(eventId: UUID, userId: UUID, inviter: UUID) {
    update(
        "insert into event_member (event_id, user_id, role, status, invited_by) values (?, ?, ?, 'INVITED', ?) " +
            "on conflict (event_id, user_id) do update set status = 'INVITED', rsvp = 'NOT_ANSWERED', " +
            "invited_by = excluded.invited_by where event_member.status = 'LEFT'",
        eventId,
        userId,
        Role.DEFAULT_FOR_NEW_MEMBER.name,
        inviter,
    )
}

/** The guest created for [email] in this event, created on the first invitation and reused after. */
private fun Connection.emailInvitation(eventId: UUID, inviter: UUID, email: String, name: String): InvitationLinkDto {
    query("select user_id, token from event_invitation where event_id = ? and email = ?", eventId, email) {
        it.getObject(1, UUID::class.java) to it.getString(2)
    }.firstOrNull()?.let { (userId, token) ->
        addMember(eventId, userId, inviter)
        return InvitationLinkDto(email, userId.toString(), token)
    }
    val userId = query(
        "insert into app_user (display_name, email, guest_event_id) values (?, ?, ?) returning id",
        name,
        email,
        eventId,
    ) { it.getObject(1, UUID::class.java) }.single()
    addMember(eventId, userId, inviter)
    val token = newLinkToken()
    update(
        "insert into event_invitation (token, event_id, user_id, email, invited_by) values (?, ?, ?, ?, ?)",
        token,
        eventId,
        userId,
        email,
        inviter,
    )
    return InvitationLinkDto(email, userId.toString(), token)
}

/**
 * Whom a planner may add by id: an account (not a guest) that chose to be searchable, or someone they already
 * share a group or an event with. Anything else answers like an unknown id.
 */
private fun Connection.canInvite(inviter: UUID, userId: UUID): Boolean = query(
    """
    select 1 from app_user u where u.id = ? and u.guest_event_id is null and (
        u.searchable
        or exists (select 1 from group_member a join group_member b on a.group_id = b.group_id
                   where a.user_id = ? and b.user_id = u.id)
        or exists (select 1 from event_member a join event_member b on a.event_id = b.event_id
                   where a.user_id = ? and b.user_id = u.id)
    )
    """.trimIndent(),
    userId,
    inviter,
    inviter,
) { 1 }.isNotEmpty()

private fun Connection.isGroupMember(groupId: UUID, userId: UUID): Boolean =
    query("select 1 from group_member where group_id = ? and user_id = ?", groupId, userId) { 1 }.isNotEmpty()

private fun Connection.accountWithEmail(email: String): UUID? = query(
    "select u.id from app_user u where u.guest_event_id is null and (lower(u.email) = ? " +
        "or exists (select 1 from auth_identity i where i.user_id = u.id and lower(i.email) = ?)) limit 1",
    email,
    email,
) { it.getObject(1, UUID::class.java) }.firstOrNull()

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
