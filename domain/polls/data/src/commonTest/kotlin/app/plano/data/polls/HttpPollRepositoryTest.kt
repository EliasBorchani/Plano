package app.plano.data.polls

import app.plano.domain.polls.model.VoteChoice
import app.plano.utilities.network.NetworkConfig
import app.plano.utilities.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpPollRepositoryTest {
    private val pollJson = """
        {"id":"p1","options":[{"id":"o1","date":"2026-12-05"}],
         "votes":[{"optionId":"o1","userId":"u1","choice":"PREFER"},{"optionId":"o1","userId":"u2","choice":"FUTURE"}]}
    """.trimIndent()

    @Test
    fun voteSendsChoiceAndStoresReturnedPoll() = runTest {
        var seen = ""
        val engine = MockEngine { request ->
            seen = "${request.method.value} ${request.url.encodedPath}"
            respond(pollJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repository = HttpPollRepository(createHttpClient(NetworkConfig("http://test/api/"), engine))

        repository.vote("e1", "o1", VoteChoice.PREFER)

        assertEquals("${HttpMethod.Put.value} /api/events/e1/poll/options/o1/vote", seen)
        val poll = repository.observe("e1").first()!!
        assertEquals("2026-12-05", poll.options.single().date.toString())
        // The unknown "FUTURE" choice is dropped.
        assertEquals(listOf(VoteChoice.PREFER), poll.votes.map { it.choice })
    }
}
