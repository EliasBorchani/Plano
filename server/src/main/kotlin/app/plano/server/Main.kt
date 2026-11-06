package app.plano.server

import app.plano.domain.auth.model.AuthProvider
import app.plano.server.auth.AccessTokens
import app.plano.server.auth.Auth
import app.plano.server.auth.DevAppleVerifier
import app.plano.server.auth.IdTokenVerifier
import app.plano.server.auth.JwksIdTokenVerifier
import app.plano.server.auth.RefreshTokens
import app.plano.server.auth.Users
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
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

private val log = LoggerFactory.getLogger("app.plano.server")

class Deps(
    val db: Db,
    verifiers: Map<AuthProvider, IdTokenVerifier>,
    cookieSecure: Boolean,
    jwtSecret: ByteArray,
    accessTtl: Duration = Duration.ofMinutes(15),
    refreshTtl: Duration = Duration.ofDays(30),
    publicApiPath: String = "/api",
    clock: Clock = Clock.systemUTC(),
) {
    val auth = Auth(
        users = Users(db),
        accessTokens = AccessTokens(jwtSecret, accessTtl, clock),
        refreshTokens = RefreshTokens(db, refreshTtl, clock),
        verifiers = verifiers,
        cookieSecure = cookieSecure,
        publicApiPath = publicApiPath,
    )
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
        authRoutes(deps.auth)
        eventRoutes(deps.db, deps.auth)
        pollRoutes(deps.db, deps.auth)
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
        jwtSecret = config.jwtSecret ?: run {
            // Dev only: access tokens die with the process, and the client refreshes them on the next call.
            log.warn("JWT_SECRET not set: using a random one for this run")
            ByteArray(32).also(SecureRandom()::nextBytes)
        },
        accessTtl = config.accessTtl,
        refreshTtl = config.refreshTtl,
        publicApiPath = config.publicApiPath,
    )
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { plano(deps) }.start(wait = true)
}
