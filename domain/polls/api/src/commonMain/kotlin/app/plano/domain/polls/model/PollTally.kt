package app.plano.domain.polls.model

data class OptionTally(
    val optionId: String,
    val prefer: Int,
    val cannot: Int,
    val ifMajority: Int,
) {
    val preferAndIfMajority: Int get() = prefer + ifMajority
}

data class TallyResult(
    /** In date order. */
    val options: List<OptionTally>,
    /** Most PREFER votes; `null` while nobody voted PREFER. */
    val leaderOnPrefer: String?,
    /** Most PREFER + IF_MAJORITY votes; `null` while nobody voted either. */
    val leaderWithIfMajority: String?,
) {
    fun of(optionId: String): OptionTally? = options.firstOrNull { it.optionId == optionId }
}

/**
 * Two leaders, both shown to planners (owner's decision): one counting PREFER
 * alone, one counting PREFER + IF_MAJORITY. Each tie goes to fewer CANNOT,
 * then to the earlier date.
 */
object PollTally {
    fun tally(poll: DatePoll): TallyResult {
        val byOption = poll.votes.groupBy { it.optionId }
        val tallies = poll.options.sortedBy { it.date }.map { option ->
            val votes = byOption[option.id].orEmpty()
            OptionTally(
                optionId = option.id,
                prefer = votes.count { it.choice == VoteChoice.PREFER },
                cannot = votes.count { it.choice == VoteChoice.CANNOT },
                ifMajority = votes.count { it.choice == VoteChoice.IF_MAJORITY },
            )
        }
        return TallyResult(
            options = tallies,
            leaderOnPrefer = leader(tallies) { it.prefer },
            leaderWithIfMajority = leader(tallies) { it.preferAndIfMajority },
        )
    }

    private fun leader(tallies: List<OptionTally>, score: (OptionTally) -> Int): String? =
        tallies
            .filter { score(it) > 0 }
            // sortedWith is stable and tallies are in date order: equal keys keep the earlier date first.
            .sortedWith(compareByDescending<OptionTally> { score(it) }.thenBy { it.cannot })
            .firstOrNull()
            ?.optionId
}
