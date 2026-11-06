package app.plano.domain.auth.model

sealed interface Session {
    /** Not checked with the server yet. */
    data object Unknown : Session

    data object SignedOut : Session

    data class SignedIn(val user: User) : Session
}
