package app.plano.domain.events.repository

import app.plano.domain.events.model.Event
import app.plano.domain.events.model.EventSummary
import app.plano.domain.events.model.NewEvent
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    /** The signed-in user's events; `null` until the first load ends. */
    fun observeMine(): Flow<List<EventSummary>?>

    suspend fun refreshMine()

    /** Returns the new event's id. */
    suspend fun create(event: NewEvent): String

    /** `null` until loaded. Collecting it does not fetch: call [refresh]. */
    fun observe(eventId: String): Flow<Event?>

    suspend fun refresh(eventId: String)

    /** Joins through the event link (idempotent). */
    suspend fun join(eventId: String)
}
