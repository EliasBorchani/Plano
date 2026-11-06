package app.plano.features.event

import app.plano.domain.polls.model.VoteChoice

sealed interface EventIntent {
    data object Refresh : EventIntent

    data class SetNewDate(val isoDate: String) : EventIntent

    data object AddDate : EventIntent

    /** Choosing the current choice again removes the vote. */
    data class Vote(val optionId: String, val choice: VoteChoice) : EventIntent
}

enum class AddDateError {
    DATE_INVALID,
    DATE_IN_PAST,
    DATE_ALREADY_OFFERED,
}
