package app.plano.server

import app.plano.contract.AddDateOptionRequest
import app.plano.contract.CreateEventRequest
import app.plano.contract.DatePollDto
import app.plano.contract.JoinAsGuestRequest
import app.plano.contract.VoteRequest
import app.plano.contract.CreatedDto
import app.plano.contract.SignInRequest
import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.server.auth.DevAppleVerifier
import app.plano.server.auth.IdTokenVerifier
import app.plano.server.auth.VerifiedIdentity
import app.plano.server.links.SafeFetcher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand, to expire tokens without waiting. */
class TestClock(private var now: Instant = Instant.parse("2026-11-06T12:00:00Z")) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}

// Stands in for Google: the token is the subject.
private val fakeGoogle = IdTokenVerifier {
    VerifiedIdentity(subject = it, email = "$it@example.com", name = it.replaceFirstChar(Char::uppercase))
}

val TEST_JWT_SECRET = "test-secret-that-is-at-least-32-bytes-long".toByteArray()

/** The app on a fresh database. Routes are served at the root, so cookie paths have no `/api` prefix. */
fun api(
    clock: Clock = Clock.systemUTC(),
    linkFetcher: SafeFetcher = SafeFetcher(),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = TestDatabase().use { database ->
    testApplication {
        application {
            plano(
                Deps(
                    db = database.db,
                    verifiers = mapOf(AuthProvider.GOOGLE to fakeGoogle, AuthProvider.APPLE to DevAppleVerifier),
                    cookieSecure = false,
                    jwtSecret = TEST_JWT_SECRET,
                    publicApiPath = "",
                    clock = clock,
                    linkFetcher = linkFetcher,
                ),
            )
        }
        block()
    }
}

/** One browser: keeps its cookies. */
fun ApplicationTestBuilder.user(): HttpClient = createClient {
    install(HttpCookies)
    install(ContentNegotiation) { json() }
}

/** A native app: no cookie jar, tokens go in headers and bodies. */
fun ApplicationTestBuilder.nativeApp(): HttpClient = createClient {
    install(ContentNegotiation) { json() }
}

suspend fun HttpClient.signIn(provider: String, token: String): UserDto =
    post("/auth/$provider") {
        contentType(ContentType.Application.Json)
        setBody(SignInRequest(idToken = token))
    }.body()

suspend fun HttpClient.createEvent(title: String = "Dîner"): String =
    post("/events") {
        contentType(ContentType.Application.Json)
        setBody(CreateEventRequest(title = title, type = "DINNER"))
    }.body<CreatedDto>().id

suspend fun HttpClient.joinAsGuest(eventId: String, name: String): HttpResponse =
    post("/auth/guest/$eventId") {
        contentType(ContentType.Application.Json)
        setBody(JoinAsGuestRequest(name))
    }

suspend fun HttpClient.addDate(eventId: String, date: String): DatePollDto =
    post("/events/$eventId/poll/options") {
        contentType(ContentType.Application.Json)
        setBody(AddDateOptionRequest(date))
    }.body()

suspend fun HttpClient.vote(eventId: String, optionId: String, choice: String?): HttpResponse =
    put("/events/$eventId/poll/options/$optionId/vote") {
        contentType(ContentType.Application.Json)
        setBody(VoteRequest(choice))
    }
