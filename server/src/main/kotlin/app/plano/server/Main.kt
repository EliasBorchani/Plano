package app.plano.server

import app.plano.domain.auth.model.AuthProvider
import app.plano.server.auth.DevAppleVerifier
import app.plano.server.auth.IdTokenVerifier
import app.plano.server.auth.JwksIdTokenVerifier
import app.plano.server.auth.Sessions
import app.plano.server.auth.authRoutes
import app.plano.server.events.eventRoutes
import app.plano.server.polls.pollRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("app.plano.server")

class Deps(
    val db: Db,
    val verifiers: Map<AuthProvider, IdTokenVerifier>,
    val cookieSecure: Boolean,
) {
    val sessions = Sessions(db)
}

fun verifiersFor(config: Config): Map<AuthProvider, IdTokenVerifier> = buildMap {
    config.googleClientId?.let { put(AuthProvider.GOOGLE, JwksIdTokenVerifier.google(it)) }
    when {
        config.appleServicesId != null -> put(AuthProvider.APPLE, JwksIdTokenVerifier.apple(config.appleServicesId))
        config.isDev -> put(AuthProvider.APPLE, DevAppleVerifier)
    }
}

fun Application.plano(deps: Deps) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CallLogging)
    installErrors()
    routing {
        authRoutes(deps.sessions, deps.verifiers, deps.cookieSecure)
        eventRoutes(deps.db, deps.sessions)
        pollRoutes(deps.db, deps.sessions)
    }
}

fun main() {
    val config = Config.fromEnv()
    val verifiers = verifiersFor(config)
    log.info("Sign-in providers: {} (APP_ENV={})", verifiers.keys.ifEmpty { "none" }, config.appEnv)
    val deps = Deps(
        db = Db.connect(config.databaseUrl, config.databaseUser, config.databasePassword),
        verifiers = verifiers,
        cookieSecure = config.cookieSecure,
    )
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { plano(deps) }.start(wait = true)
}
