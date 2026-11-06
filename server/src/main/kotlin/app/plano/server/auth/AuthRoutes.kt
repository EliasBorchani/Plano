package app.plano.server.auth

import app.plano.contract.Routes
import app.plano.contract.SignInRequest
import app.plano.domain.auth.model.AuthProvider
import app.plano.server.notFound
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.authRoutes(
    sessions: Sessions,
    verifiers: Map<AuthProvider, IdTokenVerifier>,
    cookieSecure: Boolean,
) {
    post(Routes.signIn("{provider}")) {
        val provider = call.parameters["provider"]?.let(AuthProvider::fromWireName) ?: notFound()
        val verifier = verifiers[provider] ?: notFound()
        val body = call.receive<SignInRequest>()
        val identity = verifier.verify(body.idToken)
        val (user, token) = sessions.signIn(provider, identity, body.displayName)
        call.response.cookies.append(
            Cookie(
                name = SESSION_COOKIE,
                value = token,
                maxAge = SESSION_LIFETIME.seconds.toInt(),
                path = "/",
                httpOnly = true,
                secure = cookieSecure,
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
        call.respond(user.toDto())
    }

    post(Routes.LOGOUT) {
        call.request.cookies[SESSION_COOKIE]?.let { sessions.delete(it) }
        call.response.cookies.append(
            Cookie(name = SESSION_COOKIE, value = "", maxAge = 0, path = "/", httpOnly = true, secure = cookieSecure),
        )
        call.respond(HttpStatusCode.NoContent)
    }

    get(Routes.ME) {
        call.respond(call.requireUser(sessions).toDto())
    }
}
