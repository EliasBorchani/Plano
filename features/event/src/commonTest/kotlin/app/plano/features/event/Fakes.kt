package app.plano.features.event

import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.GuestInvite
import app.plano.domain.auth.model.Session
import app.plano.domain.auth.model.User
import app.plano.domain.auth.repository.AuthRepository
import app.plano.domain.events.model.Event
import app.plano.domain.events.model.EventSummary
import app.plano.domain.events.model.EventType
import app.plano.domain.events.model.Member
import app.plano.domain.events.model.NewEvent
import app.plano.domain.events.model.Role
import app.plano.domain.events.repository.EventRepository
import app.plano.domain.polls.model.DateOption
import app.plano.domain.polls.model.DatePoll
import app.plano.domain.polls.model.Vote
import app.plano.domain.polls.model.VoteChoice
import app.plano.domain.polls.repository.PollRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

const val ME = "u-me"

class FakeAuth(guest: Boolean = false) : AuthRepository {
    override val session = MutableStateFlow<Session>(Session.SignedIn(User(ME, "Me", null, guestEventId = "e1".takeIf { guest })))

    override suspend fun restore() = Unit

    override suspend fun signIn(provider: AuthProvider, idToken: String, displayName: String?) = Unit

    override suspend fun signOut() = Unit

    override suspend fun guestInvite(eventId: String): GuestInvite = error("not used")

    override suspend fun joinAsGuest(eventId: String, displayName: String) = Unit

    override suspend fun continueAsGuest(eventId: String, guestId: String) = Unit
}

class FakeEvents(role: Role = Role.OWNER) : EventRepository {
    var joined = false
    var refreshes = 0
    private val event = MutableStateFlow<Event?>(null)
    private val loaded = Event("e1", "Dîner", EventType.DINNER, null, role, listOf(Member(ME, "Me", role)))

    override fun observeMine(): Flow<List<EventSummary>?> = MutableStateFlow(null)

    override suspend fun refreshMine() = Unit

    override suspend fun create(event: NewEvent): String = "e1"

    override fun observe(eventId: String): Flow<Event?> = event

    override suspend fun refresh(eventId: String) {
        refreshes++
        event.value = loaded
    }

    override suspend fun join(eventId: String) {
        joined = true
    }
}

class FakePolls : PollRepository {
    val poll = MutableStateFlow<DatePoll?>(null)
    private var next = 0

    override fun observe(eventId: String): Flow<DatePoll?> = poll

    override suspend fun refresh(eventId: String) {
        if (poll.value == null) poll.value = DatePoll("p1", emptyList(), emptyList())
    }

    override suspend fun addDate(eventId: String, date: LocalDate) {
        poll.update { it!!.copy(options = it.options + DateOption("o${next++}", date)) }
    }

    override suspend fun vote(eventId: String, optionId: String, choice: VoteChoice?) {
        poll.update { poll ->
            val others = poll!!.votes.filterNot { it.optionId == optionId && it.userId == ME }
            poll.copy(votes = others + listOfNotNull(choice?.let { Vote(optionId, ME, it) }))
        }
    }
}
