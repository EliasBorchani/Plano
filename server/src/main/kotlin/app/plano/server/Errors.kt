package app.plano.server

import app.plano.contract.ErrorDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import java.util.UUID

class ApiError(
    val status: HttpStatusCode,
    val code: String,
    message: String = code,
) : Exception(message)

fun notFound(): Nothing = throw ApiError(HttpStatusCode.NotFound, "not_found")

fun unauthorized(): Nothing = throw ApiError(HttpStatusCode.Unauthorized, "unauthorized")

fun forbidden(): Nothing = throw ApiError(HttpStatusCode.Forbidden, "forbidden")

fun invalid(code: String): Nothing = throw ApiError(HttpStatusCode.BadRequest, code)

/** A malformed id names nothing that exists: 404, like a missing one. */
fun String?.toUuidOr404(): UUID = this?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: notFound()

fun Application.installErrors() {
    install(StatusPages) {
        exception<ApiError> { call, error ->
            call.respond(error.status, ErrorDto(error.code, error.message ?: error.code))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "Malformed request"))
        }
    }
}
