package app.plano.utilities.network

import app.plano.utilities.kotlinext.AppError
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class NetworkConfig(
    /** Absolute, ending with `/` (e.g. `https://plano.app/api/`). */
    val baseUrl: String,
)

val PlanoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

expect fun httpClientEngine(): HttpClientEngine

fun createHttpClient(
    config: NetworkConfig,
    engine: HttpClientEngine = httpClientEngine(),
    extraConfig: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(PlanoJson)
    }

    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.value >= 300) {
                throw AppError.fromHttpStatus(response.status.value, response.bodyAsText().ifBlank { null })
            }
        }
    }

    defaultRequest {
        url(config.baseUrl)
        contentType(ContentType.Application.Json)
    }

    extraConfig()
}
