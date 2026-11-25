package app.plano.server.auth

import app.plano.contract.GuestDto
import app.plano.contract.GuestInviteDto
import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.GuestName
import app.plano.domain.auth.model.GuestNameError
import app.plano.domain.events.model.Role
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.invalid
import app.plano.server.notFound
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
) {
    val isGuest: Boolean get() = guestEventId != null

    fun toDto() = UserDto(id = id.toString(), displayName = displayName, email = email, guestEventId = guestEventId?.toString())
}

private const val USER_COLUMNS = "u.id, u.display_name, u.email, u.guest_event_id"

private fun ResultSet.toUser() = AuthUser(
    id = getObject(1, UUID::class.java),
    displayName = getString(2),
    email = getString(3),
    guestEventId = getObject(4, UUID::class.java),
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

    /** The event link's public face. 404 when the event does not exist. */
    suspend fun guestInvite(eventId: UUID): GuestInviteDto = db.tx {
        val guests = query(
            "select id, display_name from app_user where guest_event_id = ? order by created_at",
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
        update("insert into event_member (event_id, user_id, role) values (?, ?, ?)", eventId, id, Role.DEFAULT_FOR_NEW_MEMBER.name)
        AuthUser(id, name, null, eventId)
    }

    /** The guest [guestId] of [eventId], or 404 (unknown, an account, or a guest of another event). */
    suspend fun guestOf(eventId: UUID, guestId: UUID): AuthUser = db.tx {
        findUser(guestId)?.takeIf { it.guestEventId == eventId } ?: notFound()
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
            "insert into event_member (event_id, user_id, role, joined_at) " +
                "select event_id, ?, role, joined_at from event_member where user_id = ? on conflict do nothing",
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
