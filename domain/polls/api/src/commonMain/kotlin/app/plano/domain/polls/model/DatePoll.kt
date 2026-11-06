package app.plano.domain.polls.model

import kotlinx.datetime.LocalDate

/** In display order, from yes to no. */
enum class VoteChoice {
    /** "I prefer" */
    PREFER,

    /** "Only if it is the majority" */
    IF_MAJORITY,

    /** "I can't" */
    CANNOT,
    ;

    companion object {
        fun fromName(name: String): VoteChoice? = entries.firstOrNull { it.name == name }
    }
}

data class DateOption(
    val id: String,
    val date: LocalDate,
)

data class Vote(
    val optionId: String,
    val userId: String,
    val choice: VoteChoice,
)

data class DatePoll(
    val id: String,
    val options: List<DateOption>,
    val votes: List<Vote>,
) {
    fun myChoice(optionId: String, userId: String): VoteChoice? =
        votes.firstOrNull { it.optionId == optionId && it.userId == userId }?.choice

    /** A date can be offered once per poll. */
    fun canAdd(date: LocalDate): Boolean = options.none { it.date == date }
}
