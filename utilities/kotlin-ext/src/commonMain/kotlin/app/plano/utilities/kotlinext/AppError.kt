package app.plano.utilities.kotlinext

/**
 * The one failure type that crosses from data modules to view models, so
 * features never depend on the network layer.
 */
class AppError(
    val kind: Kind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: kind.name, cause) {
    enum class Kind {
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        INVALID,
        NETWORK,
        SERVER,
    }

    companion object {
        fun fromHttpStatus(status: Int, message: String?): AppError = AppError(
            kind = when (status) {
                400, 422 -> Kind.INVALID
                401 -> Kind.UNAUTHORIZED
                403 -> Kind.FORBIDDEN
                404 -> Kind.NOT_FOUND
                409 -> Kind.CONFLICT
                else -> Kind.SERVER
            },
            message = message,
        )
    }
}

/** For UI state: anything that is not an [AppError] is treated as a network failure. */
val Throwable.errorKind: AppError.Kind
    get() = (this as? AppError)?.kind ?: AppError.Kind.NETWORK
