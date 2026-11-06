package app.plano.features.events

import app.plano.domain.events.model.Event
import app.plano.domain.events.model.EventSummary
import app.plano.domain.events.model.NewEvent
import app.plano.domain.events.repository.EventRepository
import app.plano.features.events.create.CreateEventEvent
import app.plano.features.events.create.CreateEventIntent
import app.plano.features.events.create.CreateEventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEventViewModelTest {
    private class Recording : EventRepository {
        var created: NewEvent? = null

        override fun observeMine(): Flow<List<EventSummary>?> = MutableStateFlow(null)

        override suspend fun refreshMine() = Unit

        override suspend fun create(event: NewEvent): String {
            created = event
            return "e42"
        }

        override fun observe(eventId: String): Flow<Event?> = MutableStateFlow(null)

        override suspend fun refresh(eventId: String) = Unit

        override suspend fun join(eventId: String) = Unit
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun errorsShowOnlyAfterSubmit() = runTest {
        val repository = Recording()
        val vm = CreateEventViewModel(repository)
        assertTrue(vm.state.value.errors.isEmpty())
        assertEquals(27, vm.state.value.types.size)

        vm.onIntent(CreateEventIntent.Submit)
        assertEquals(listOf("TITLE_MISSING"), vm.state.value.errors)
        assertNull(repository.created)
    }

    @Test
    fun submitSendsTheTrimmedEventAndEmitsCreated() = runTest {
        val repository = Recording()
        val vm = CreateEventViewModel(repository)
        val created = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertIs<CreateEventEvent.Created>(vm.events.first()).also { assertEquals("e42", it.eventId) }
        }

        vm.onIntent(CreateEventIntent.SetTitle("  Pique-nique  "))
        vm.onIntent(CreateEventIntent.SetType("PICNIC"))
        vm.onIntent(CreateEventIntent.Submit)

        assertEquals("Pique-nique", repository.created?.title)
        assertEquals("PICNIC", repository.created?.type?.name)
        assertNull(repository.created?.description)
        assertTrue(created.isCompleted)
    }
}
