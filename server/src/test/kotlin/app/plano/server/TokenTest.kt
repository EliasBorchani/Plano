package app.plano.server

import app.plano.contract.RefreshRequest
import app.plano.contract.SignInRequest
import app.plano.contract.TokenPairDto
import app.plano.contract.UserDto
import app.plano.server.auth.ACCESS_COOKIE
import app.plano.server.auth.AccessTokens
import app.plano.server.auth.Principal
import app.plano.server.auth.REFRESH_COOKIE
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenTest {
    private val clock = TestClock()

    private suspend fun HttpClient.refreshCookie(): String =
        cookies("http://localhost/auth/refresh").single { it.name == REFRESH_COOKIE }.value

    private suspend fun HttpClient.accessCookie(): String =
        cookies("http://localhost/me").single { it.name == ACCESS_COOKIE }.value

    private suspend fun HttpClient.refreshWithBody(token: String): HttpResponse = post("/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(token))
    }

    @Test
    fun signInSetsAnAccessCookieForEverythingAndARefreshCookieForAuthOnly() = api(clock) {
        val response = user().post("/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(SignInRequest(idToken = "alice"))
        }
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        val access = setCookies.single { it.startsWith("$ACCESS_COOKIE=") }
        val refresh = setCookies.single { it.startsWith("$REFRESH_COOKIE=") }
        assertTrue("Path=/;" in access || access.endsWith("Path=/"), access)
        assertTrue("Path=/auth" in refresh, refresh)
        assertTrue("HttpOnly" in access && "HttpOnly" in refresh)
        assertTrue("Max-Age=900" in access, access)
    }

    @Test
    fun anExpiredAccessTokenIsRenewedByARefresh() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        clock.advance(Duration.ofMinutes(16))
        assertEquals(HttpStatusCode.Unauthorized, alice.get("/me").status)

        val oldRefresh = alice.refreshCookie()
        assertEquals(HttpStatusCode.NoContent, alice.post("/auth/refresh").status)
        assertNotEquals(oldRefresh, alice.refreshCookie())
        assertEquals("Alice", alice.get("/me").body<UserDto>().displayName)
    }

    @Test
    fun aReplacedTokenUsedRightAwayIsRefusedWithoutRevokingTheFamily() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        val first = alice.refreshCookie()
        alice.post("/auth/refresh")

        clock.advance(Duration.ofSeconds(10))
        assertEquals(HttpStatusCode.Unauthorized, alice.refreshWithBody(first).status)
        assertEquals(HttpStatusCode.NoContent, alice.post("/auth/refresh").status)
    }

    @Test
    fun aReplacedTokenUsedLaterRevokesTheWholeFamily() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        val stolen = alice.refreshCookie()
        alice.post("/auth/refresh")

        clock.advance(Duration.ofSeconds(31))
        assertEquals(HttpStatusCode.Unauthorized, alice.refreshWithBody(stolen).status)
        assertEquals(HttpStatusCode.Unauthorized, alice.post("/auth/refresh").status)
    }

    @Test
    fun anotherDeviceKeepsItsOwnFamily() = api(clock) {
        val laptop = user().apply { signIn("google", "alice") }
        val phone = user().apply { signIn("google", "alice") }
        laptop.post("/auth/logout")
        assertEquals(HttpStatusCode.NoContent, phone.post("/auth/refresh").status)
    }

    @Test
    fun logoutRevokesTheRefreshToken() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        val refresh = alice.refreshCookie()
        assertEquals(HttpStatusCode.NoContent, alice.post("/auth/logout").status)
        assertEquals(HttpStatusCode.Unauthorized, alice.refreshWithBody(refresh).status)
    }

    @Test
    fun anExpiredRefreshTokenIsRefused() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        clock.advance(Duration.ofDays(31))
        assertEquals(HttpStatusCode.Unauthorized, alice.post("/auth/refresh").status)
    }

    @Test
    fun eachRefreshStartsAFresh30Days() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        repeat(3) {
            clock.advance(Duration.ofDays(20))
            assertEquals(HttpStatusCode.NoContent, alice.post("/auth/refresh").status)
        }
    }

    @Test
    fun nativeClientsUseABearerHeaderAndTheBody() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        val native = nativeApp()

        val pair = native.refreshWithBody(alice.refreshCookie()).body<TokenPairDto>()
        assertEquals(900, pair.accessExpiresInSeconds)
        assertEquals("Alice", native.get("/me") { bearerAuth(pair.accessToken) }.body<UserDto>().displayName)
        assertEquals(HttpStatusCode.OK, native.get("/events") { bearerAuth(pair.accessToken) }.status)
        assertEquals(HttpStatusCode.OK, native.refreshWithBody(pair.refreshToken).status)
    }

    @Test
    fun forgedAccessTokensAreRefused() = api(clock) {
        val alice = user().apply { signIn("google", "alice") }
        val userId = UUID.fromString(alice.get("/me").body<UserDto>().id)
        val otherKey = AccessTokens("another-secret-that-is-at-least-32-bytes".toByteArray(), Duration.ofMinutes(15), clock)
        val forged = otherKey.sign(Principal(userId, UUID.randomUUID()))
        val anonymous = nativeApp()

        assertEquals(HttpStatusCode.Unauthorized, anonymous.get("/me") { bearerAuth(forged) }.status)
        assertEquals(HttpStatusCode.Unauthorized, anonymous.get("/me") { bearerAuth("not-a-jwt") }.status)
        // The genuine token still passes: the refusals above come from the signature.
        assertEquals(HttpStatusCode.OK, anonymous.get("/me") { bearerAuth(alice.accessCookie()) }.status)
    }
}
