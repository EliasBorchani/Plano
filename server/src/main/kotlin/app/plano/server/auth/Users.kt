package app.plano.server.auth

import app.plano.contract.GuestDto
import app.plano.contract.GuestInviteDto
import app.plano.contract.InvitationPreviewDto
import app.plano.contract.PersonDto
import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.GuestName
import app.plano.domain.auth.model.GuestNameError
import app.plano.domain.events.model.Role
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.invalid
import app.plano.server.notFound
import app.plano.server.unauthorized
import app.plano.server.query
import app.plano.server.update
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

data class AuthUser(
    val id: UUID,
    val displayName: String,
    val email: String?,
    val guestEventId: UUID? = null,
    val searchable: Boolean = false,
) {
    val isGuest: Boolean get() = guestEventId != null

    fun toDto() = UserDto(
        id = id.toString(),
        displayName = displayName,
        email = email,
        guestEventId = guestEventId?.toString(),
        searchable = searchable,
    )
}

private const val USER_COLUMNS = "u.id, u.display_name, u.email, u.guest_event_id, u.searchable"

private fun ResultSet.toUser() = AuthUser(
    id = getObject(1, UUID::class.java),
    displayName = getString(2),
    email = getString(3),
    guestEventId = getObject(4, UUID::class.java),
    searchable = getBoolean(5),
)

class Users(private val db: Db) {
    /**
     * Finds or creates the account behind [identity]. When the browser was signed in as [guestId], the guest
     * becomes that account: converted in place for a new identity, merged into the account for a known one.
     */
    suspend fun signIn(
        provider: AuthProvider,
        identity: VerifiedIdentity,
        displayName: String?,
        guestId: UUID? = null,
    ): AuthUser = db.tx {
        val existing = query(
            "select $USER_COLUMNS from auth_identity i join app_user u on u.id = i.user_id " +
                "where i.provider = ? and i.subject = ?",
            provider.wireName,
            identity.subject,
        ) { it.toUser() }.firstOrNull()
        val guest = guestId?.let { lockGuest(it) }

        when {
            existing != null -> existing.also { if (guest != null) mergeGuest(guest, into = it) }
            guest != null -> {
                // The guest keeps the name the group knows them by.
                update("update app_user set guest_event_id = null, email = ? where id = ?", identity.email, guest.id)
                addIdentity(provider, identity, guest.id)
                guest.copy(email = identity.email, guestEventId = null)
            }
            else -> {
                val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: identity.name
                    ?: identity.email?.substringBefore('@')
                    ?: "Guest"
                val id = query(
                    "insert into app_user (display_name, email) values (?, ?) returning id",
                    name,
                    identity.email,
                ) { it.getObject(1, UUID::class.java) }.single()
                addIdentity(provider, identity, id)
                AuthUser(id, name, identity.email)
            }
        }
    }

    suspend fun find(id: UUID): AuthUser? = db.tx { findUser(id) }

    /**
     * The event link's public face. 404 when the event does not exist. People invited by email are left out: they
     * come back through their personal link, and « C'est moi » must not let someone else take their place.
     */
    suspend fun guestInvite(eventId: UUID): GuestInviteDto = db.tx {
        val guests = query(
            "select id, display_name from app_user where guest_event_id = ? and email is null order by created_at",
            eventId,
        ) { GuestDto(it.getString(1), it.getString(2)) }
        query("select id, title, type from event where id = ?", eventId) {
            GuestInviteDto(eventId = it.getString(1), title = it.getString(2), type = it.getString(3), guests = guests)
        }.firstOrNull() ?: notFound()
    }

    /** A new guest of [eventId], member with the friend-group default role. */
    suspend fun createGuest(eventId: UUID, rawName: String): AuthUser = db.tx {
        GuestName.validate(rawName)?.let { invalid(it.name) }
        val name = GuestName.normalize(rawName)
        // Serializes guest creation per event, so two people typing the same name at once cannot both get it.
        query("select 1 from event where id = ? for update", eventId) { 1 }.firstOrNull() ?: notFound()
        val taken = query(
            "select u.display_name from event_member m join app_user u on u.id = m.user_id where m.event_id = ?",
            eventId,
        ) { it.getString(1) }.any { GuestName.sameName(it, name) }
        if (taken) throw ApiError(HttpStatusCode.Conflict, GuestNameError.NAME_TAKEN.name)

        val id = query(
            "insert into app_user (display_name, guest_event_id) values (?, ?) returning id",
            name,
            eventId,
        ) { it.getObject(1, UUID::class.java) }.single()
        update(
            "insert into event_member (event_id, user_id, role, status) values (?, ?, ?, 'JOINED')",
            eventId,
            id,
            Role.DEFAULT_FOR_NEW_MEMBER.name,
        )
        AuthUser(id, name, null, eventId)
    }

    /** The guest [guestId] of [eventId], or 404 (unknown, an account, a guest of another event, invited by email). */
    suspend fun guestOf(eventId: UUID, guestId: UUID): AuthUser = db.tx {
        findUser(guestId)?.takeIf { it.guestEventId == eventId && it.email == null } ?: notFound()
    }

    /** What a personal invitation link shows. 404 once the invitee made an account (they sign in with it). */
    suspend fun invitation(token: String): Pair<InvitationPreviewDto, AuthUser> = db.tx {
        query(
            "select e.id, e.title, e.type, $USER_COLUMNS from event_invitation i join event e on e.id = i.event_id " +
                "join app_user u on u.id = i.user_id where i.token = ? and u.guest_event_id = i.event_id",
            token,
        ) {
            val user = AuthUser(
                id = it.getObject(4, UUID::class.java),
                displayName = it.getString(5),
                email = it.getString(6),
                guestEventId = it.getObject(7, UUID::class.java),
            )
            InvitationPreviewDto(it.getString(1), it.getString(2), it.getString(3), user.displayName) to user
        }.firstOrNull() ?: notFound()
    }

    /** Accounts only: a guest cannot be found by name, they belong to one event. */
    suspend fun setSearchable(userId: UUID, searchable: Boolean): AuthUser = db.tx {
        val user = findUser(userId) ?: unauthorized()
        if (user.isGuest) throw ApiError(HttpStatusCode.Forbidden, "GUEST_CANNOT_BE_SEARCHABLE")
        update("update app_user set searchable = ? where id = ?", searchable, userId)
        user.copy(searchable = searchable)
    }

    /** Searchable accounts whose name contains [text], case ignored (accents are not, for now). */
    suspend fun search(text: String, caller: UUID): List<PersonDto> = db.tx {
        val pattern = "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        query(
            "select id, display_name from app_user where searchable and guest_event_id is null and id <> ? " +
                "and display_name ilike ? order by display_name limit 20",
            caller,
            pattern,
        ) { PersonDto(it.getString(1), it.getString(2)) }
    }

    private fun Connection.findUser(id: UUID): AuthUser? =
        query("select $USER_COLUMNS from app_user u where u.id = ?", id) { it.toUser() }.firstOrNull()

    private fun Connection.lockGuest(id: UUID): AuthUser? =
        query("select $USER_COLUMNS from app_user u where u.id = ? for update", id) { it.toUser() }
            .firstOrNull()
            ?.takeIf { it.isGuest }

    private fun Connection.addIdentity(provider: AuthProvider, identity: VerifiedIdentity, userId: UUID) {
        update(
            "insert into auth_identity (provider, subject, user_id, email) values (?, ?, ?, ?)",
            provider.wireName,
            identity.subject,
            userId,
            identity.email,
        )
    }

    /**
     * Moves what the guest did into [into], then deletes the guest (its refresh tokens go with it).
     * Where both voted on the same date, the account's vote stays (owner's rule); the account's role stays too.
     */
    private fun Connection.mergeGuest(guest: AuthUser, into: AuthUser) {
        update(
            "insert into event_member (event_id, user_id, role, joined_at, status, rsvp, invited_by) " +
                "select event_id, ?, role, joined_at, status, rsvp, invited_by from event_member where user_id = ? " +
                "on conflict do nothing",
            into.id,
            guest.id,
        )
        update(
            "insert into vote (option_id, user_id, choice, updated_at) " +
                "select option_id, ?, choice, updated_at from vote where user_id = ? on conflict do nothing",
            into.id,
            guest.id,
        )
        update("update poll_option set created_by = ? where created_by = ?", into.id, guest.id)
        update("delete from app_user where id = ?", guest.id)
    }
}
