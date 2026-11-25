package app.plano.features.signin

import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.Guest
import app.plano.domain.auth.model.GuestInvite
import app.plano.domain.auth.model.Session
import app.plano.domain.auth.model.User
import app.plano.domain.auth.repository.AuthRepository
import app.plano.features.signin.guest.GuestEntryIntent
import app.plano.features.signin.guest.GuestEntryViewModel
import app.plano.utilities.kotlinext.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeGuestAuth(private val nameTakenOnServer: Boolean = false) : AuthRepository {
    override val session = MutableStateFlow<Session>(Session.SignedOut)
    val joined = mutableListOf<String>()

    override suspend fun restore() = Unit

    override suspend fun signIn(provider: AuthProvider, idToken: String, displayName: String?) = Unit

    override suspend fun signOut() = Unit

    override suspend fun guestInvite(eventId: String): GuestInvite {
        if (eventId != "e1") throw AppError(AppError.Kind.NOT_FOUND)
        return GuestInvite("e1", "Dîner", "DINNER", listOf(Guest("g1", "Bob")))
    }

    override suspend fun joinAsGuest(eventId: String, displayName: String) {
        if (nameTakenOnServer) throw AppError(AppError.Kind.CONFLICT)
        joined += displayName
        session.value = Session.SignedIn(User("g2", displayName, null, guestEventId = eventId))
    }

    override suspend fun continueAsGuest(eventId: String, guestId: String) {
        session.value = Session.SignedIn(User(guestId, "Bob", null, guestEventId = eventId))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GuestEntryViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsTheEventAndItsGuests() = runTest {
        val state = GuestEntryViewModel("e1", FakeGuestAuth()).state.value
        assertFalse(state.loading)
        assertEquals("Dîner", state.eventTitle)
        assertEquals(listOf("Bob"), state.guests.map { it.displayName })

        assertTrue(GuestEntryViewModel("nope", FakeGuestAuth()).state.value.notFound)
    }

    @Test
    fun joinChecksTheNameFirst() = runTest {
        val auth = FakeGuestAuth()
        val vm = GuestEntryViewModel("e1", auth)
        vm.onIntent(GuestEntryIntent.Join)
        assertEquals("NAME_MISSING", vm.state.value.error)

        // Already a guest here: the list offers « C'est moi » instead.
        vm.onIntent(GuestEntryIntent.SetName(" bob "))
        vm.onIntent(GuestEntryIntent.Join)
        assertEquals("NAME_TAKEN", vm.state.value.error)

        vm.onIntent(GuestEntryIntent.SetName("  Léa  "))
        vm.onIntent(GuestEntryIntent.Join)
        assertEquals(listOf("Léa"), auth.joined)
        assertTrue(auth.session.value is Session.SignedIn)
    }

    @Test
    fun aNameTakenByAnAccountComesBackFromTheServer() = runTest {
        val vm = GuestEntryViewModel("e1", FakeGuestAuth(nameTakenOnServer = true))
        vm.onIntent(GuestEntryIntent.SetName("Alice"))
        vm.onIntent(GuestEntryIntent.Join)
        assertEquals("NAME_TAKEN", vm.state.value.error)
    }

    @Test
    fun theSessionDecidesWhenTheGuestEntryShows() {
        fun guestOf(eventId: String) = SignInState(SessionStatus.SIGNED_IN, UserUi("g1", "Bob", true, eventId))
        assertTrue(SignInState(SessionStatus.SIGNED_OUT).needsGuestEntry("e1"))
        assertFalse(SignInState(SessionStatus.UNKNOWN).needsGuestEntry("e1"))
        assertFalse(guestOf("e1").needsGuestEntry("e1"))
        assertTrue(guestOf("e2").needsGuestEntry("e1"))
        assertFalse(SignInState(SessionStatus.SIGNED_IN, UserUi("u1", "Alice", false, null)).needsGuestEntry("e1"))
    }
}
