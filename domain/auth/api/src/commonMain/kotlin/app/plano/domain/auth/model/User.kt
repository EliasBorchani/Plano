package app.plano.domain.auth.model

data class User(
    val id: String,
    val displayName: String,
    val email: String?,
    /** Set for a guest (name only, no account): the one event this guest belongs to. */
    val guestEventId: String? = null,
) {
    val isGuest: Boolean get() = guestEventId != null
}
