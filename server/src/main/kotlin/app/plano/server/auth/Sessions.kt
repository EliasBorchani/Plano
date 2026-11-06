package app.plano.server.auth

import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.server.Db
import app.plano.server.query
import app.plano.server.unauthorized
import app.plano.server.update
import io.ktor.server.application.ApplicationCall
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

const val SESSION_COOKIE = "plano_session"
val SESSION_LIFETIME: Duration = Duration.ofDays(30)

data class AuthUser(
    val id: UUID,
    val displayName: String,
    val email: String?,
) {
    fun toDto() = UserDto(id = id.toString(), displayName = displayName, email = email)
}

class Sessions(private val db: Db) {
    private val random = SecureRandom()

    /** Finds or creates the account behind [identity], then opens a session. Returns the user and the raw token. */
    suspend fun signIn(provider: AuthProvider, identity: VerifiedIdentity, displayName: String?): Pair<AuthUser, String> = db.tx {
        val existing = query(
            "select u.id, u.display_name, u.email from auth_identity i join app_user u on u.id = i.user_id " +
                "where i.provider = ? and i.subject = ?",
            provider.wireName,
            identity.subject,
        ) { AuthUser(it.getObject(1, UUID::class.java), it.getString(2), it.getString(3)) }.firstOrNull()

        val user = existing ?: run {
            val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: identity.name
                ?: identity.email?.substringBefore('@')
                ?: "Guest"
            val id = query(
                "insert into app_user (display_name, email) values (?, ?) returning id",
                name,
                identity.email,
            ) { it.getObject(1, UUID::class.java) }.single()
            update(
                "insert into auth_identity (provider, subject, user_id, email) values (?, ?, ?, ?)",
                provider.wireName,
                identity.subject,
                id,
                identity.email,
            )
            AuthUser(id, name, identity.email)
        }

        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        update(
            "insert into session (token_hash, user_id, expires_at) values (?, ?, ?)",
            hash(token),
            user.id,
            OffsetDateTime.now().plus(SESSION_LIFETIME),
        )
        user to token
    }

    suspend fun find(token: String): AuthUser? = db.tx {
        query(
            "select u.id, u.display_name, u.email from session s join app_user u on u.id = s.user_id " +
                "where s.token_hash = ? and s.expires_at > now()",
            hash(token),
        ) { AuthUser(it.getObject(1, UUID::class.java), it.getString(2), it.getString(3)) }.firstOrNull()
    }

    suspend fun delete(token: String) {
        db.tx { update("delete from session where token_hash = ?", hash(token)) }
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}

suspend fun ApplicationCall.requireUser(sessions: Sessions): AuthUser {
    val token = request.cookies[SESSION_COOKIE] ?: unauthorized()
    return sessions.find(token) ?: unauthorized()
}
