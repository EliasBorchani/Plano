package app.plano.server.auth

import app.plano.server.ApiError
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

data class VerifiedIdentity(
    val subject: String,
    val email: String?,
    val name: String?,
)

/** Checks an ID token issued by a sign-in provider and says who it names. */
fun interface IdTokenVerifier {
    suspend fun verify(idToken: String): VerifiedIdentity
}

/** Google and Apple both sign RS256 JWTs with keys published as a JWKS. */
class JwksIdTokenVerifier(
    jwksUrl: String,
    private val issuers: Set<String>,
    audience: String,
) : IdTokenVerifier {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        jwsKeySelector = JWSVerificationKeySelector(
            JWSAlgorithm.RS256,
            JWKSourceBuilder.create<SecurityContext>(URI(jwksUrl).toURL()).build(),
        )
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(audience, null, setOf("sub", "iss", "exp"))
    }

    override suspend fun verify(idToken: String): VerifiedIdentity {
        // The first call fetches the provider's keys over the network.
        val claims = withContext(Dispatchers.IO) {
            runCatching { processor.process(idToken, null) }.getOrNull()
        }
        if (claims == null || claims.issuer !in issuers) throw ApiError(HttpStatusCode.Unauthorized, "invalid_token")
        return VerifiedIdentity(
            subject = claims.subject,
            email = claims.getStringClaim("email"),
            name = claims.getStringClaim("name"),
        )
    }

    companion object {
        fun google(clientId: String) = JwksIdTokenVerifier(
            jwksUrl = "https://www.googleapis.com/oauth2/v3/certs",
            issuers = setOf("https://accounts.google.com", "accounts.google.com"),
            audience = clientId,
        )

        fun apple(servicesId: String) = JwksIdTokenVerifier(
            jwksUrl = "https://appleid.apple.com/auth/keys",
            issuers = setOf("https://appleid.apple.com"),
            audience = servicesId,
        )
    }
}

/**
 * Development stand-in for Sign in with Apple, which needs a paid Apple
 * Developer account and an HTTPS domain. Accepts `dev:<name>`. Only wired when APP_ENV=dev.
 */
object DevAppleVerifier : IdTokenVerifier {
    override suspend fun verify(idToken: String): VerifiedIdentity {
        val name = idToken.removePrefix("dev:").trim()
        if (!idToken.startsWith("dev:") || name.isEmpty()) throw ApiError(HttpStatusCode.Unauthorized, "invalid_token")
        return VerifiedIdentity(subject = "dev:${name.lowercase()}", email = null, name = name)
    }
}
