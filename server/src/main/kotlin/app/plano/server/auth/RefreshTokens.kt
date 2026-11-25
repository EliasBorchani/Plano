package app.plano.server.auth

import app.plano.server.Db
import app.plano.server.query
import app.plano.server.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

data class IssuedRefresh(
    val principal: Principal,
    /** Raw token, given to the client once; only its SHA-256 is stored. */
    val token: String,
)

/**
 * Opaque refresh tokens, rotated at each use. Expiry slides: every rotation starts a fresh [ttl].
 * A replaced token used again after [reuseGrace] is treated as stolen and revokes its whole family.
 */
class RefreshTokens(
    private val db: Db,
    val ttl: Duration,
    private val clock: Clock,
    private val reuseGrace: Duration = Duration.ofSeconds(30),
) {
    private val random = SecureRandom()

    suspend fun issue(userId: UUID): IssuedRefresh = db.tx {
        insert(Principal(userId = userId, familyId = UUID.randomUUID()))
    }

    /** A new token in the same family, or null when [raw] may not be used (unknown, expired, replaced, revoked). */
    suspend fun rotate(raw: String): IssuedRefresh? = db.tx {
        val row = query(
            "select family_id, user_id, expires_at, replaced_at, revoked_at from refresh_token " +
                "where token_hash = ? for update",
            hash(raw),
        ) {
            Row(
                principal = Principal(userId = it.getObject(2, UUID::class.java), familyId = it.getObject(1, UUID::class.java)),
                expiresAt = it.getObject(3, OffsetDateTime::class.java).toInstant(),
                replacedAt = it.getObject(4, OffsetDateTime::class.java)?.toInstant(),
                revoked = it.getObject(5) != null,
            )
        }.firstOrNull() ?: return@tx null

        val now = clock.instant()
        when {
            row.revoked -> null
            row.replacedAt != null -> {
                // Two tabs refreshing at once send the same token: the late one is refused, nothing revoked.
                if (Duration.between(row.replacedAt, now) > reuseGrace) revokeFamily(row.principal.familyId, now)
                null
            }
            !now.isBefore(row.expiresAt) -> null
            else -> {
                update("update refresh_token set replaced_at = ? where token_hash = ?", now.toOffset(), hash(raw))
                insert(row.principal)
            }
        }
    }

    /** Who [raw] belongs to while it may still be used (not revoked, not expired). */
    suspend fun userOf(raw: String): UUID? = db.tx {
        query(
            "select user_id from refresh_token where token_hash = ? and revoked_at is null and expires_at > ?",
            hash(raw),
            clock.instant().toOffset(),
        ) { it.getObject(1, UUID::class.java) }.firstOrNull()
    }

    /** Logout: the family's refresh tokens stop working; its access tokens expire on their own. */
    suspend fun revoke(raw: String) {
        db.tx {
            val familyId = query("select family_id from refresh_token where token_hash = ?", hash(raw)) {
                it.getObject(1, UUID::class.java)
            }.firstOrNull() ?: return@tx
            revokeFamily(familyId, clock.instant())
        }
    }

    private fun Connection.revokeFamily(familyId: UUID, now: Instant) {
        update("update refresh_token set revoked_at = ? where family_id = ? and revoked_at is null", now.toOffset(), familyId)
    }

    private fun Connection.insert(principal: Principal): IssuedRefresh {
        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val now = clock.instant()
        update(
            "insert into refresh_token (token_hash, family_id, user_id, created_at, expires_at) values (?, ?, ?, ?, ?)",
            hash(token),
            principal.familyId,
            principal.userId,
            now.toOffset(),
            now.plus(ttl).toOffset(),
        )
        return IssuedRefresh(principal, token)
    }

    private data class Row(
        val principal: Principal,
        val expiresAt: Instant,
        val replacedAt: Instant?,
        val revoked: Boolean,
    )

    private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
