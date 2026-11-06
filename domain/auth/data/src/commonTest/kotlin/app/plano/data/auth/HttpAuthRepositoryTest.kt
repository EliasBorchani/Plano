package app.plano.data.auth

import app.plano.contract.Routes
import app.plano.domain.auth.model.Session
import app.plano.utilities.kotlinext.AppError
import app.plano.utilities.network.NetworkConfig
import app.plano.utilities.network.RefreshConfig
import app.plano.utilities.network.SessionEvents
import app.plano.utilities.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class HttpAuthRepositoryTest {
    private var refreshWorks = true

    private val engine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/api/me" -> respond(
                """{"id":"u1","displayName":"Alice"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            "/api/auth/refresh" -> respond("", if (refreshWorks) HttpStatusCode.NoContent else HttpStatusCode.Unauthorized)
            else -> respond("", HttpStatusCode.Unauthorized)
        }
    }

    private val events = SessionEvents()
    private val client = createHttpClient(
        NetworkConfig("http://test/api/", RefreshConfig(Routes.REFRESH, Routes.AUTH_PREFIX)),
        engine,
        events,
    )
    private val repository = HttpAuthRepository(client, events)

    @Test
    fun aFailedRefreshOnAnyCallSignsOut() = runTest {
        repository.restore()
        assertIs<Session.SignedIn>(repository.session.value)

        refreshWorks = false
        // Any route works: the client asks for a refresh on the 401, the refresh fails, the session ends.
        assertFailsWith<AppError> { client.get(Routes.EVENTS) }
        assertEquals(Session.SignedOut, repository.session.value)
    }
}
