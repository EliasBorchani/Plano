package app.plano.features.events.create

import kotlin.js.JsExport

@JsExport
data class CreateEventState(
    val title: String = "",
    /** `EventType` name. */
    val type: String,
    val description: String = "",
    /** Every `EventType` name, in display order. */
    val types: List<String>,
    /** `NewEventError` names, shown once the user tried to submit. */
    val errors: List<String> = emptyList(),
    val submitting: Boolean = false,
    val error: String? = null,
)

@JsExport
sealed class CreateEventEvent {
    class Created(val eventId: String) : CreateEventEvent()
}
