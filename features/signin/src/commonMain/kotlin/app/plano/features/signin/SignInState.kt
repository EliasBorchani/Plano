package app.plano.features.signin

import kotlin.js.JsExport

@JsExport
enum class SessionStatus { UNKNOWN, SIGNED_OUT, SIGNED_IN }

@JsExport
data class UserUi(
    val id: String,
    val displayName: String,
    /** A guest has no account: signing out loses them on this device, and they cannot create events. */
    val isGuest: Boolean,
    /** The one event a guest belongs to. */
    val guestEventId: String?,
)

@JsExport
data class SignInState(
    val status: SessionStatus = SessionStatus.UNKNOWN,
    val user: UserUi? = null,
    val busy: Boolean = false,
    /** An `AppError.Kind` name (`UNAUTHORIZED`, `NETWORK`…), translated by the UI. */
    val error: String? = null,
) {
    /**
     * On an event link: show the guest entry (type a name, or « C'est moi ») instead of the event.
     * True when signed out, and for a guest of another event (a guest belongs to one event only).
     */
    fun needsGuestEntry(eventId: String): Boolean = when (status) {
        SessionStatus.UNKNOWN -> false
        SessionStatus.SIGNED_OUT -> true
        SessionStatus.SIGNED_IN -> user?.guestEventId?.let { it != eventId } ?: false
    }
}
