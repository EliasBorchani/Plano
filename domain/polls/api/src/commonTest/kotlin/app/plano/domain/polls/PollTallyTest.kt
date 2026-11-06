package app.plano.domain.polls

import app.plano.domain.polls.model.DateOption
import app.plano.domain.polls.model.DatePoll
import app.plano.domain.polls.model.PollTally
import app.plano.domain.polls.model.Vote
import app.plano.domain.polls.model.VoteChoice.CANNOT
import app.plano.domain.polls.model.VoteChoice.IF_MAJORITY
import app.plano.domain.polls.model.VoteChoice.PREFER
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PollTallyTest {
    private val fri = DateOption("fri", LocalDate(2026, 12, 4))
    private val sat = DateOption("sat", LocalDate(2026, 12, 5))
    private val sun = DateOption("sun", LocalDate(2026, 12, 6))

    private fun poll(vararg votes: Vote) = DatePoll("p", listOf(sun, fri, sat), votes.toList())

    @Test
    fun noVotesHasNoLeader() {
        val result = PollTally.tally(poll())
        assertNull(result.leaderOnPrefer)
        assertNull(result.leaderWithIfMajority)
        assertEquals(listOf("fri", "sat", "sun"), result.options.map { it.optionId })
    }

    @Test
    fun cannotVotesAloneGiveNoLeader() {
        val result = PollTally.tally(poll(Vote("fri", "a", CANNOT)))
        assertNull(result.leaderOnPrefer)
        assertNull(result.leaderWithIfMajority)
    }

    @Test
    fun theTwoLeadersCanDiffer() {
        val result = PollTally.tally(
            poll(
                Vote("sat", "a", PREFER),
                Vote("sat", "b", PREFER),
                Vote("fri", "c", PREFER),
                Vote("fri", "d", IF_MAJORITY),
                Vote("fri", "e", IF_MAJORITY),
            ),
        )
        assertEquals("sat", result.leaderOnPrefer)
        assertEquals("fri", result.leaderWithIfMajority)
        assertEquals(3, result.of("fri")?.preferAndIfMajority)
    }

    @Test
    fun ifMajorityAloneLeadsOnlyTheSecondRanking() {
        val result = PollTally.tally(poll(Vote("sat", "a", IF_MAJORITY)))
        assertNull(result.leaderOnPrefer)
        assertEquals("sat", result.leaderWithIfMajority)
    }

    @Test
    fun tieGoesToFewerCannotThenEarlierDate() {
        val tieOnCannot = PollTally.tally(
            poll(
                Vote("fri", "a", PREFER),
                Vote("sat", "b", PREFER),
                Vote("fri", "c", CANNOT),
            ),
        )
        assertEquals("sat", tieOnCannot.leaderOnPrefer)

        val tieOnDate = PollTally.tally(poll(Vote("sun", "a", PREFER), Vote("sat", "b", PREFER)))
        assertEquals("sat", tieOnDate.leaderOnPrefer)
    }
}
