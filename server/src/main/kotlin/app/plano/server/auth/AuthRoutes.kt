package app.plano.server.auth

import app.plano.contract.JoinAsGuestRequest
import app.plano.contract.RefreshRequest
import app.plano.contract.Routes
import app.plano.contract.UpdateProfileRequest
import app.plano.contract.SignInRequest
import app.plano.contract.TokenPairDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.server.invalid
import app.plano.server.notFound
import app.plano.server.toUuidOr404
import app.plano.server.unauthorized
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import java.time.Duration

const val ACCESS_COOKIE = "plano_access"
const val REFRESH_COOKIE = "plano_refresh"

/** Everything the auth routes need, built once in `Main.kt`. */
class Auth(
    val users: Users,
    val accessTokens: AccessTokens,
    val refreshTokens: RefreshTokens,
    val verifiers: Map<AuthProvider, IdTokenVerifier>,
    val cookieSecure: Boolean,
    /** Where the browser sees the API (`/api` behind the web proxy). Cookie paths are matched against it. */
    val publicApiPath: String,
)

/** The caller, from a bearer header (native apps) or the access cookie (web). 401 when missing or expired. */
fun ApplicationCall.requireUser(auth: Auth): Principal {
    val bearer = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring("Bearer ".length)
        ?.trim()
    val token = bearer ?: request.cookies[ACCESS_COOKIE] ?: unauthorized()
    return auth.accessTokens.verify(token) ?: unauthorized()
}

fun Route.authRoutes(auth: Auth) {
    post(Routes.signIn("{provider}")) {
        val provider = call.parameters["provider"]?.let(AuthProvider::fromWireName) ?: notFound()
        val verifier = auth.verifiers[provider] ?: notFound()
        val body = call.receive<SignInRequest>()
        val identity = verifier.verify(body.idToken)
        val previous = call.request.cookies[REFRESH_COOKIE]
        // Signed in as a guest on this browser: the guest becomes (or joins) the account.
        val guestId = previous?.let { auth.refreshTokens.userOf(it) }
            ?.let { auth.users.find(it) }
            ?.takeIf { it.isGuest }
            ?.id
        val user = auth.users.signIn(provider, identity, body.displayName, guestId)
        call.signInAs(auth, user)
    }

    get(Routes.guestInvite("{eventId}")) {
        val eventId = call.parameters["eventId"].toUuidOr404()
        call.respond(auth.users.guestInvite(eventId))
    }

    post(Routes.guestInvite("{eventId}")) {
        val eventId = call.parameters["eventId"].toUuidOr404()
        val body = call.receive<JoinAsGuestRequest>()
        call.signInAs(auth, auth.users.createGuest(eventId, body.displayName))
    }

    // No secret on purpose (owner, 2026-11-25): anyone with the event link may continue as one of its guests, so a
    // guest who changes device finds their votes again. Guests are bound to one event, so this opens nothing else.
    post(Routes.continueAsGuest("{eventId}", "{guestId}")) {
        val eventId = call.parameters["eventId"].toUuidOr404()
        val guestId = call.parameters["guestId"].toUuidOr404()
        call.signInAs(auth, auth.users.guestOf(eventId, guestId))
    }

    get(Routes.invitation("{token}")) {
        val token = call.parameters["token"] ?: notFound()
        call.respond(auth.users.invitation(token).first)
    }

    // The personal link of someone invited by email: it signs them in on any device, until they make an account.
    post(Routes.invitation("{token}")) {
        val token = call.parameters["token"] ?: notFound()
        call.signInAs(auth, auth.users.invitation(token).second)
    }

    post(Routes.REFRESH) {
        val fromBody = call.refreshTokenFromBody()
        val raw = fromBody ?: call.request.cookies[REFRESH_COOKIE]
        val issued = raw?.let { auth.refreshTokens.rotate(it) }
        if (issued == null) {
            if (fromBody == null) call.clearTokenCookies(auth)
            unauthorized()
        }
        if (fromBody != null) {
            call.respond(
                TokenPairDto(
                    accessToken = auth.accessTokens.sign(issued.principal),
                    refreshToken = issued.token,
                    accessExpiresInSeconds = auth.accessTokens.ttl.seconds.toInt(),
                ),
            )
        } else {
            call.setTokenCookies(auth, issued)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post(Routes.LOGOUT) {
        val raw = call.refreshTokenFromBody() ?: call.request.cookies[REFRESH_COOKIE]
        raw?.let { auth.refreshTokens.revoke(it) }
        call.clearTokenCookies(auth)
        call.respond(HttpStatusCode.NoContent)
    }

    get(Routes.ME) {
        val principal = call.requireUser(auth)
        // A deleted account keeps valid access tokens until they expire: treat it as signed out.
        val user = auth.users.find(principal.userId) ?: unauthorized()
        call.respond(user.toDto())
    }

    patch(Routes.ME) {
        val principal = call.requireUser(auth)
        val body = call.receive<UpdateProfileRequest>()
        call.respond(auth.users.setSearchable(principal.userId, body.searchable).toDto())
    }

    get(Routes.USER_SEARCH) {
        val principal = call.requireUser(auth)
        val text = call.request.queryParameters["q"].orEmpty()
        if (text.trim().length < 2) invalid("QUERY_TOO_SHORT")
        call.respond(auth.users.search(text, principal.userId))
    }
}

/** Ends this browser's previous session (if any) and starts one for [user]. */
private suspend fun ApplicationCall.signInAs(auth: Auth, user: AuthUser) {
    request.cookies[REFRESH_COOKIE]?.let { auth.refreshTokens.revoke(it) }
    setTokenCookies(auth, auth.refreshTokens.issue(user.id))
    respond(user.toDto())
}

/** Web calls send no body (the token is in a cookie); native apps send `{refreshToken}`. */
private suspend fun ApplicationCall.refreshTokenFromBody(): String? {
    val text = receiveText()
    if (text.isBlank()) return null
    return runCatching { Json.decodeFromString<RefreshRequest>(text).refreshToken }
        .getOrElse { throw BadRequestException("Malformed body") }
}

private fun ApplicationCall.setTokenCookies(auth: Auth, issued: IssuedRefresh) {
    response.cookies.append(cookie(auth, ACCESS_COOKIE, auth.accessTokens.sign(issued.principal), "/", auth.accessTokens.ttl))
    response.cookies.append(cookie(auth, REFRESH_COOKIE, issued.token, refreshPath(auth), auth.refreshTokens.ttl))
}

private fun ApplicationCall.clearTokenCookies(auth: Auth) {
    response.cookies.append(cookie(auth, ACCESS_COOKIE, "", "/", Duration.ZERO))
    response.cookies.append(cookie(auth, REFRESH_COOKIE, "", refreshPath(auth), Duration.ZERO))
}

// The refresh token is only sent to the auth routes (refresh, logout, sign-in), never with ordinary calls.
private fun refreshPath(auth: Auth) = auth.publicApiPath.trimEnd('/') + "/" + Routes.AUTH_PREFIX.trimEnd('/')

private fun cookie(auth: Auth, name: String, value: String, path: String, maxAge: Duration) = Cookie(
    name = name,
    value = value,
    maxAge = maxAge.seconds.toInt(),
    path = path,
    httpOnly = true,
    secure = auth.cookieSecure,
    extensions = mapOf("SameSite" to "Lax"),
)
