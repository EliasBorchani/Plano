package app.plano.server

import app.plano.contract.AddDateOptionRequest
import app.plano.contract.CreateEventRequest
import app.plano.contract.CreatedDto
import app.plano.contract.DatePollDto
import app.plano.contract.EventDto
import app.plano.contract.EventSummaryDto
import app.plano.contract.SignInRequest
import app.plano.contract.UserDto
import app.plano.contract.VoteRequest
import app.plano.domain.auth.model.AuthProvider
import app.plano.server.auth.DevAppleVerifier
import app.plano.server.auth.IdTokenVerifier
import app.plano.server.auth.VerifiedIdentity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiTest {
    // Stands in for Google: the token is the subject.
    private val fakeGoogle = IdTokenVerifier { VerifiedIdentity(subject = it, email = "$it@example.com", name = it.replaceFirstChar(Char::uppercase)) }

    private fun api(block: suspend ApplicationTestBuilder.() -> Unit) = TestDatabase().use { database ->
        testApplication {
            application {
                plano(
                    Deps(
                        db = database.db,
                        verifiers = mapOf(AuthProvider.GOOGLE to fakeGoogle, AuthProvider.APPLE to DevAppleVerifier),
                        cookieSecure = false,
                    ),
                )
            }
            block()
        }
    }

    private fun ApplicationTestBuilder.user(): HttpClient = createClient {
        install(HttpCookies)
        install(ContentNegotiation) { json() }
    }

    private suspend fun HttpClient.signIn(provider: String, token: String): UserDto =
        post("/auth/$provider") {
            contentType(ContentType.Application.Json)
            setBody(SignInRequest(idToken = token))
        }.body()

    private suspend fun HttpClient.createEvent(title: String = "Dîner"): String =
        post("/events") {
            contentType(ContentType.Application.Json)
            setBody(CreateEventRequest(title = title, type = "DINNER"))
        }.body<CreatedDto>().id

    private val inAWeek = LocalDate.now().plusDays(7).toString()

    @Test
    fun signedOutCallsAre401() = api {
        assertEquals(HttpStatusCode.Unauthorized, user().get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, user().get("/events").status)
    }

    @Test
    fun signInOpensASessionAndReusesTheAccount() = api {
        val alice = user()
        val first = alice.signIn("google", "alice")
        assertEquals("Alice", first.displayName)
        assertEquals(first, alice.get("/me").body<UserDto>())

        val again = user().signIn("google", "alice")
        assertEquals(first.id, again.id)

        alice.post("/auth/logout")
        assertEquals(HttpStatusCode.Unauthorized, alice.get("/me").status)
    }

    @Test
    fun devAppleSignIn() = api {
        assertEquals("Bob", user().signIn("apple", "dev:Bob").displayName)
        val refused = user().post("/auth/apple") {
            contentType(ContentType.Application.Json)
            setBody(SignInRequest(idToken = "not-a-dev-token"))
        }
        assertEquals(HttpStatusCode.Unauthorized, refused.status)
    }

    @Test
    fun createEventRunsTheSharedValidation() = api {
        val alice = user().apply { signIn("google", "alice") }
        val blank = alice.post("/events") {
            contentType(ContentType.Application.Json)
            setBody(CreateEventRequest(title = "   ", type = "DINNER"))
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)

        val id = alice.createEvent("  Dîner chez Léa  ")
        val event = alice.get("/events/$id").body<EventDto>()
        assertEquals("Dîner chez Léa", event.title)
        assertEquals("OWNER", event.myRole)
        assertEquals(listOf(EventSummaryDto(id, "Dîner chez Léa", "DINNER", "OWNER")), alice.get("/events").body<List<EventSummaryDto>>())
    }

    @Test
    fun strangersSee404UntilTheyJoin() = api {
        val alice = user().apply { signIn("google", "alice") }
        val bob = user().apply { signIn("apple", "dev:Bob") }
        val id = alice.createEvent()

        assertEquals(HttpStatusCode.NotFound, bob.get("/events/$id").status)
        assertEquals(HttpStatusCode.NotFound, bob.get("/events/$id/poll").status)
        assertEquals(HttpStatusCode.NotFound, bob.get("/events/not-a-uuid").status)

        assertEquals(HttpStatusCode.NoContent, bob.post("/events/$id/join").status)
        assertEquals(HttpStatusCode.NoContent, bob.post("/events/$id/join").status)
        val event = bob.get("/events/$id").body<EventDto>()
        assertEquals("PLANNER", event.myRole)
        assertEquals(listOf("Alice", "Bob"), event.members.map { it.displayName })
    }

    @Test
    fun datePollVotes() = api {
        val alice = user().apply { signIn("google", "alice") }
        val bob = user().apply { signIn("apple", "dev:Bob") }
        val id = alice.createEvent()
        bob.post("/events/$id/join")

        val poll = alice.post("/events/$id/poll/options") {
            contentType(ContentType.Application.Json)
            setBody(AddDateOptionRequest(inAWeek))
        }.body<DatePollDto>()
        val optionId = poll.options.single().id

        val duplicate = bob.post("/events/$id/poll/options") {
            contentType(ContentType.Application.Json)
            setBody(AddDateOptionRequest(inAWeek))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val past = alice.post("/events/$id/poll/options") {
            contentType(ContentType.Application.Json)
            setBody(AddDateOptionRequest("2020-01-01"))
        }
        assertEquals(HttpStatusCode.BadRequest, past.status)

        suspend fun HttpClient.vote(choice: String?) = put("/events/$id/poll/options/$optionId/vote") {
            contentType(ContentType.Application.Json)
            setBody(VoteRequest(choice))
        }

        alice.vote("PREFER")
        bob.vote("CANNOT")
        bob.vote("IF_MAJORITY")
        assertEquals(setOf("PREFER", "IF_MAJORITY"), alice.get("/events/$id/poll").body<DatePollDto>().votes.map { it.choice }.toSet())

        assertEquals(1, bob.vote(null).body<DatePollDto>().votes.size)
        assertEquals(HttpStatusCode.BadRequest, bob.vote("MAYBE").status)
    }
}
