package app.plano.features.signin.guest

import kotlin.js.JsExport

@JsExport
data class GuestUi(
    val id: String,
    val displayName: String,
)

/** The landing of an event link for someone without a session here: a name is enough to take part. */
@JsExport
data class GuestEntryState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val eventTitle: String = "",
    /** `EventType` name. */
    val eventType: String = "",
    /** Existing guests of this event, to continue as one of them from another device. */
    val guests: List<GuestUi> = emptyList(),
    val name: String = "",
    val busy: Boolean = false,
    /** A `GuestNameError` name or an `AppError.Kind` name. */
    val error: String? = null,
)
