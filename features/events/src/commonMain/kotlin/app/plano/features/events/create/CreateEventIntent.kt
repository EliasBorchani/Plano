package app.plano.features.events.create

sealed interface CreateEventIntent {
    data class SetTitle(val value: String) : CreateEventIntent

    data class SetType(val value: String) : CreateEventIntent

    data class SetDescription(val value: String) : CreateEventIntent

    data object Submit : CreateEventIntent
}
