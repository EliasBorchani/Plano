package app.plano.data.events

import app.plano.contract.CreateEventRequest
import app.plano.contract.CreatedDto
import app.plano.contract.EventDto
import app.plano.contract.EventSummaryDto
import app.plano.contract.MemberDto
import app.plano.contract.Routes
import app.plano.domain.events.model.Event
import app.plano.domain.events.model.EventSummary
import app.plano.domain.events.model.EventType
import app.plano.domain.events.model.Member
import app.plano.domain.events.model.NewEvent
import app.plano.domain.events.model.Role
import app.plano.domain.events.repository.EventRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory cache in front of the server; screens observe it, mutations refresh it. */
class HttpEventRepository(
    private val client: HttpClient,
) : EventRepository {
    private val mine = MutableStateFlow<List<EventSummary>?>(null)
    private val events = MutableStateFlow<Map<String, Event>>(emptyMap())

    override fun observeMine(): Flow<List<EventSummary>?> = mine

    override suspend fun refreshMine() {
        mine.value = client.get(Routes.EVENTS).body<List<EventSummaryDto>>().map { it.toDomain() }
    }

    override suspend fun create(event: NewEvent): String {
        val created = client.post(Routes.EVENTS) {
            setBody(CreateEventRequest(title = event.title, type = event.type.name, description = event.description))
        }.body<CreatedDto>()
        refreshMine()
        return created.id
    }

    override fun observe(eventId: String): Flow<Event?> = events.map { it[eventId] }.distinctUntilChanged()

    override suspend fun refresh(eventId: String) {
        val event = client.get(Routes.event(eventId)).body<EventDto>().toDomain()
        events.update { it + (eventId to event) }
    }

    override suspend fun join(eventId: String) {
        client.post(Routes.join(eventId))
    }
}

private fun EventSummaryDto.toDomain() = EventSummary(
    id = id,
    title = title,
    type = EventType.fromName(type) ?: EventType.OTHER,
    myRole = Role.fromName(myRole) ?: Role.PARTICIPANT,
)

private fun EventDto.toDomain() = Event(
    id = id,
    title = title,
    type = EventType.fromName(type) ?: EventType.OTHER,
    description = description,
    myRole = Role.fromName(myRole) ?: Role.PARTICIPANT,
    members = members.map { it.toDomain() },
)

private fun MemberDto.toDomain() = Member(
    userId = userId,
    displayName = displayName,
    role = Role.fromName(role) ?: Role.PARTICIPANT,
)
