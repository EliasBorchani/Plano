package app.plano.domain.polls.repository

import app.plano.domain.polls.model.DatePoll
import app.plano.domain.polls.model.VoteChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PollRepository {
    /** The event's date poll; `null` until loaded. Collecting it does not fetch: call [refresh]. */
    fun observe(eventId: String): Flow<DatePoll?>

    suspend fun refresh(eventId: String)

    suspend fun addDate(eventId: String, date: LocalDate)

    /** `null` removes the vote. */
    suspend fun vote(eventId: String, optionId: String, choice: VoteChoice?)
}
