package app.plano.server.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID

/** Who made the request, read from the access token alone (no database lookup). */
data class Principal(
    val userId: UUID,
    /** The refresh token family (one sign-in on one device) this access token was issued with. */
    val familyId: UUID,
)

/** Short-lived HS256 JWTs. They cannot be revoked, which is why they live only [ttl]. */
class AccessTokens(
    secret: ByteArray,
    val ttl: Duration,
    private val clock: Clock,
) {
    init {
        require(secret.size >= 32) { "JWT_SECRET must be at least 32 bytes" }
    }

    private val signer = MACSigner(secret)
    private val verifier = MACVerifier(secret)

    fun sign(principal: Principal): String {
        val now = clock.instant()
        val claims = JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(principal.userId.toString())
            .claim(FAMILY_CLAIM, principal.familyId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)))
            .build()
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims).apply { sign(signer) }.serialize()
    }

    fun verify(token: String): Principal? = runCatching {
        val jwt = SignedJWT.parse(token)
        // Only our own algorithm: a token claiming `none` or RS256 is refused before any check.
        if (jwt.header.algorithm != JWSAlgorithm.HS256 || !jwt.verify(verifier)) return null
        val claims = jwt.jwtClaimsSet
        if (claims.issuer != ISSUER) return null
        val expiresAt = claims.expirationTime?.toInstant() ?: return null
        if (!clock.instant().isBefore(expiresAt)) return null
        Principal(
            userId = UUID.fromString(claims.subject),
            familyId = UUID.fromString(claims.getStringClaim(FAMILY_CLAIM)),
        )
    }.getOrNull()

    private companion object {
        const val ISSUER = "plano"
        const val FAMILY_CLAIM = "fam"
    }
}
