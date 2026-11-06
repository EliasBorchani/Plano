package app.plano.features.events.list

sealed interface EventListIntent {
    data object Refresh : EventListIntent
}
