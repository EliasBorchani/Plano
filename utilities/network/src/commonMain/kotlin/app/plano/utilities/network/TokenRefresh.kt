package app.plano.utilities.network

import app.plano.utilities.kotlinext.AppError
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RefreshConfig(
    /** Relative to the base URL, e.g. `auth/refresh`. */
    val refreshPath: String,
    /** Calls under this prefix (sign-in, refresh, logout) are never retried. */
    val skipPrefix: String,
)

/** Tells the auth repository that the refresh token is gone: the user has to sign in again. */
class SessionEvents {
    private val listeners = mutableListOf<() -> Unit>()

    fun onExpired(listener: () -> Unit) {
        listeners += listener
    }

    internal fun expired() {
        listeners.toList().forEach { it() }
    }
}

/**
 * On a 401, asks the server for a new access token (`POST refreshPath`, the refresh token rides in its
 * cookie) and sends the request once more. Parallel 401s share one refresh.
 */
internal fun tokenRefreshPlugin(baseUrl: String, config: RefreshConfig, events: SessionEvents) =
    createClientPlugin("TokenRefresh") {
        val basePath = Url(baseUrl).encodedPath
        val mutex = Mutex()
        // Each refresh attempt bumps the round; a request that saw an older round reuses its result.
        var round = 0
        var lastSucceeded = false

        suspend fun refreshOnce(roundSeen: Int): Boolean = mutex.withLock {
            if (round != roundSeen) return@withLock lastSucceeded
            lastSucceeded = try {
                client.post(config.refreshPath)
                true
            } catch (e: AppError) {
                if (e.kind != AppError.Kind.UNAUTHORIZED) throw e
                false
            }
            round++
            if (!lastSucceeded) events.expired()
            lastSucceeded
        }

        on(Send) { request ->
            if (request.url.encodedPath.removePrefix(basePath).startsWith(config.skipPrefix)) {
                return@on proceed(request)
            }
            val roundSeen = round
            // Depending on plugin order, a 401 arrives as a response or as the validator's AppError.
            val call: HttpClientCall? = try {
                proceed(request)
            } catch (e: AppError) {
                if (e.kind != AppError.Kind.UNAUTHORIZED) throw e
                null
            }
            if (call != null && call.response.status != HttpStatusCode.Unauthorized) return@on call
            if (!refreshOnce(roundSeen)) return@on call ?: throw AppError(AppError.Kind.UNAUTHORIZED)
            proceed(HttpRequestBuilder().takeFrom(request))
        }
    }
