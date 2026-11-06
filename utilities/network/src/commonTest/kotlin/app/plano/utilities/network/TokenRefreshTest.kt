package app.plano.utilities.network

import app.plano.utilities.kotlinext.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenRefreshTest {
    private val sent = mutableListOf<String>()
    private var tokenValid = false
    private var refreshWorks = true
    private var expiredCount = 0

    private fun MockRequestHandleScope.answer(request: HttpRequestData) = when (request.url.encodedPath) {
        "/api/auth/refresh" ->
            if (refreshWorks) {
                tokenValid = true
                respond("", HttpStatusCode.NoContent)
            } else {
                respond("", HttpStatusCode.Unauthorized)
            }
        else ->
            if (tokenValid) {
                respond("""{"ok":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("", HttpStatusCode.Unauthorized)
            }
    }

    private fun client(): HttpClient {
        val engine = MockEngine { request ->
            sent += "${request.url.encodedPath} ${request.body.toByteArray().decodeToString()}"
            answer(request)
        }
        val events = SessionEvents().apply { onExpired { expiredCount++ } }
        return createHttpClient(
            NetworkConfig("http://test/api/", RefreshConfig(refreshPath = "auth/refresh", skipPrefix = "auth/")),
            engine,
            events,
        )
    }

    @Test
    fun a401IsRefreshedThenRetriedWithTheSameBody() = runTest {
        val response = client().post("events") { setBody("""{"title":"Dîner"}""") }

        assertEquals("""{"ok":true}""", response.bodyAsText())
        assertEquals(
            listOf("/api/events {\"title\":\"Dîner\"}", "/api/auth/refresh ", "/api/events {\"title\":\"Dîner\"}"),
            sent,
        )
        assertEquals(0, expiredCount)
    }

    @Test
    fun parallel401sShareOneRefresh() = runTest {
        val client = client()
        (1..3).map { async { client.get("events/$it") } }.awaitAll()

        assertEquals(1, sent.count { it.startsWith("/api/auth/refresh") })
    }

    @Test
    fun aFailedRefreshSignsOutOnceAndFailsTheCalls() = runTest {
        refreshWorks = false
        val client = client()
        val errors = (1..3).map {
            async { assertFailsWith<AppError> { client.get("events/$it") } }
        }.awaitAll()

        assertEquals(List(3) { AppError.Kind.UNAUTHORIZED }, errors.map { it.kind })
        assertEquals(1, expiredCount)
        assertEquals(1, sent.count { it.startsWith("/api/auth/refresh") })
    }

    @Test
    fun authCallsAreNeverRetried() = runTest {
        refreshWorks = false
        val client = client()
        assertFailsWith<AppError> { client.post("auth/refresh") }
        assertFailsWith<AppError> { client.post("auth/google") }

        assertEquals(listOf("/api/auth/refresh ", "/api/auth/google "), sent)
        assertEquals(0, expiredCount)
    }

    @Test
    fun aValidTokenCostsOneRequest() = runTest {
        tokenValid = true
        client().get("me")
        assertEquals(listOf("/api/me "), sent)
    }
}
