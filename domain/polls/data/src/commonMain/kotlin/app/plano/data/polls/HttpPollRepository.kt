package app.plano.data.polls

import app.plano.contract.AddDateOptionRequest
import app.plano.contract.DatePollDto
import app.plano.contract.Routes
import app.plano.contract.VoteRequest
import app.plano.domain.polls.model.DateOption
import app.plano.domain.polls.model.DatePoll
import app.plano.domain.polls.model.Vote
import app.plano.domain.polls.model.VoteChoice
import app.plano.domain.polls.repository.PollRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** Mutations answer with the whole poll, so the cache never needs a second request. */
class HttpPollRepository(
    private val client: HttpClient,
) : PollRepository {
    private val polls = MutableStateFlow<Map<String, DatePoll>>(emptyMap())

    override fun observe(eventId: String): Flow<DatePoll?> = polls.map { it[eventId] }.distinctUntilChanged()

    override suspend fun refresh(eventId: String) {
        store(eventId, client.get(Routes.poll(eventId)).body())
    }

    override suspend fun addDate(eventId: String, date: LocalDate) {
        store(
            eventId,
            client.post(Routes.pollOptions(eventId)) { setBody(AddDateOptionRequest(date.toString())) }.body(),
        )
    }

    override suspend fun vote(eventId: String, optionId: String, choice: VoteChoice?) {
        store(
            eventId,
            client.put(Routes.vote(eventId, optionId)) { setBody(VoteRequest(choice?.name)) }.body(),
        )
    }

    private fun store(eventId: String, dto: DatePollDto) {
        polls.update { it + (eventId to dto.toDomain()) }
    }
}

internal fun DatePollDto.toDomain() = DatePoll(
    id = id,
    options = options.map { DateOption(id = it.id, date = LocalDate.parse(it.date)) },
    // A choice this client does not know yet is dropped rather than crashing the screen.
    votes = votes.mapNotNull { vote ->
        VoteChoice.fromName(vote.choice)?.let { Vote(optionId = vote.optionId, userId = vote.userId, choice = it) }
    },
)
