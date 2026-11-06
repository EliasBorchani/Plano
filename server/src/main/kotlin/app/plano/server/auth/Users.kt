package app.plano.server.auth

import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.server.Db
import app.plano.server.query
import app.plano.server.update
import java.util.UUID

data class AuthUser(
    val id: UUID,
    val displayName: String,
    val email: String?,
) {
    fun toDto() = UserDto(id = id.toString(), displayName = displayName, email = email)
}

class Users(private val db: Db) {
    /** Finds or creates the account behind [identity]. */
    suspend fun findOrCreate(provider: AuthProvider, identity: VerifiedIdentity, displayName: String?): AuthUser = db.tx {
        query(
            "select u.id, u.display_name, u.email from auth_identity i join app_user u on u.id = i.user_id " +
                "where i.provider = ? and i.subject = ?",
            provider.wireName,
            identity.subject,
        ) { AuthUser(it.getObject(1, UUID::class.java), it.getString(2), it.getString(3)) }.firstOrNull()
            ?: run {
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
    }

    suspend fun find(id: UUID): AuthUser? = db.tx {
        query("select id, display_name, email from app_user where id = ?", id) {
            AuthUser(it.getObject(1, UUID::class.java), it.getString(2), it.getString(3))
        }.firstOrNull()
    }
}
