package app.plano.features.event

import app.plano.domain.events.model.Role
import app.plano.domain.polls.model.VoteChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {
    private val today = Today { LocalDate(2026, 11, 5) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(joinOnOpen: Boolean = false, events: FakeEvents = FakeEvents(), polls: FakePolls = FakePolls()) =
        EventViewModel("e1", joinOnOpen, events, polls, FakeAuth(), today)

    @Test
    fun loadsEventAndJoinsWhenOpenedFromALink() = runTest {
        val events = FakeEvents()
        val vm = viewModel(joinOnOpen = true, events = events)
        assertTrue(events.joined)
        assertFalse(vm.state.value.loading)
        assertEquals("Dîner", vm.state.value.title)
        assertTrue(vm.state.value.members.single().isMe)
    }

    @Test
    fun participantsCannotPlan() = runTest {
        assertFalse(viewModel(events = FakeEvents(Role.PARTICIPANT)).state.value.canPlan)
    }

    @Test
    fun addDateChecksTheDateFirst() = runTest {
        val vm = viewModel()
        vm.onIntent(EventIntent.SetNewDate("nope"))
        vm.onIntent(EventIntent.AddDate)
        assertEquals(AddDateError.DATE_INVALID.name, vm.state.value.error)

        vm.onIntent(EventIntent.SetNewDate("2026-11-04"))
        vm.onIntent(EventIntent.AddDate)
        assertEquals(AddDateError.DATE_IN_PAST.name, vm.state.value.error)

        vm.onIntent(EventIntent.SetNewDate("2026-12-05"))
        vm.onIntent(EventIntent.AddDate)
        assertNull(vm.state.value.error)
        assertEquals("", vm.state.value.newDate)
        assertEquals(listOf("2026-12-05"), vm.state.value.options.map { it.date })

        vm.onIntent(EventIntent.SetNewDate("2026-12-05"))
        vm.onIntent(EventIntent.AddDate)
        assertEquals(AddDateError.DATE_ALREADY_OFFERED.name, vm.state.value.error)
    }

    @Test
    fun votingTheSameChoiceTwiceRemovesIt() = runTest {
        val vm = viewModel()
        vm.onIntent(EventIntent.SetNewDate("2026-12-05"))
        vm.onIntent(EventIntent.AddDate)
        val optionId = vm.state.value.options.single().id

        vm.onIntent(EventIntent.Vote(optionId, VoteChoice.PREFER))
        val option = vm.state.value.options.single()
        assertEquals("PREFER", option.myChoice)
        assertEquals(1, option.prefer)
        assertTrue(option.leadingOnPrefer)
        assertTrue(option.leadingWithIfMajority)

        vm.onIntent(EventIntent.Vote(optionId, VoteChoice.PREFER))
        assertNull(vm.state.value.options.single().myChoice)
        assertFalse(vm.state.value.options.single().leadingOnPrefer)
        assertFalse(vm.state.value.options.single().leadingWithIfMajority)
    }
}
