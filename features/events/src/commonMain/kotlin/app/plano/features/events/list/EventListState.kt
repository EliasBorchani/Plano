package app.plano.features.events.list

import kotlin.js.JsExport

@JsExport
data class EventItemUi(
    val id: String,
    val title: String,
    /** `EventType` name, translated by the UI. */
    val type: String,
    /** `Role` name. */
    val role: String,
)

@JsExport
data class EventListState(
    val loading: Boolean = true,
    val items: List<EventItemUi> = emptyList(),
    val error: String? = null,
)
