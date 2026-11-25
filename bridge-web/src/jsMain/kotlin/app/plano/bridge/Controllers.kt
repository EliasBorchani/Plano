package app.plano.bridge

import app.plano.domain.polls.model.VoteChoice
import app.plano.features.event.EventIntent
import app.plano.features.event.EventState
import app.plano.features.event.EventViewModel
import app.plano.features.events.create.CreateEventEvent
import app.plano.features.events.create.CreateEventIntent
import app.plano.features.events.create.CreateEventState
import app.plano.features.events.create.CreateEventViewModel
import app.plano.features.events.list.EventListIntent
import app.plano.features.events.list.EventListState
import app.plano.features.events.list.EventListViewModel
import app.plano.features.signin.SignInIntent
import app.plano.features.signin.SignInState
import app.plano.features.signin.SignInViewModel
import app.plano.features.signin.guest.GuestEntryIntent
import app.plano.features.signin.guest.GuestEntryState
import app.plano.features.signin.guest.GuestEntryViewModel

@JsExport
class SessionController internal constructor(
    private val vm: SignInViewModel,
) : Controller<SignInState>(vm) {
    fun signInWithGoogle(idToken: String) = vm.onIntent(SignInIntent.SignInWithGoogle(idToken))

    fun signInWithApple(idToken: String, displayName: String?) = vm.onIntent(SignInIntent.SignInWithApple(idToken, displayName))

    fun signOut() = vm.onIntent(SignInIntent.SignOut)
}

@JsExport
class EventListController internal constructor(
    private val vm: EventListViewModel,
) : Controller<EventListState>(vm) {
    fun refresh() = vm.onIntent(EventListIntent.Refresh)
}

@JsExport
class CreateEventController internal constructor(
    private val vm: CreateEventViewModel,
) : Controller<CreateEventState>(vm) {
    fun setTitle(value: String) = vm.onIntent(CreateEventIntent.SetTitle(value))

    fun setType(value: String) = vm.onIntent(CreateEventIntent.SetType(value))

    fun setDescription(value: String) = vm.onIntent(CreateEventIntent.SetDescription(value))

    fun submit() = vm.onIntent(CreateEventIntent.Submit)

    fun onEvent(listener: (CreateEventEvent) -> Unit): () -> Unit = listen(vm, listener)
}

@JsExport
class EventController internal constructor(
    private val vm: EventViewModel,
) : Controller<EventState>(vm) {
    fun refresh() = vm.onIntent(EventIntent.Refresh)

    fun setNewDate(isoDate: String) = vm.onIntent(EventIntent.SetNewDate(isoDate))

    fun addDate() = vm.onIntent(EventIntent.AddDate)

    /** [choice] is a `VoteChoice` name; unknown names are ignored. */
    fun vote(optionId: String, choice: String) {
        VoteChoice.fromName(choice)?.let { vm.onIntent(EventIntent.Vote(optionId, it)) }
    }
}

@JsExport
class GuestEntryController internal constructor(
    private val vm: GuestEntryViewModel,
) : Controller<GuestEntryState>(vm) {
    fun setName(value: String) = vm.onIntent(GuestEntryIntent.SetName(value))

    fun join() = vm.onIntent(GuestEntryIntent.Join)

    fun continueAs(guestId: String) = vm.onIntent(GuestEntryIntent.ContinueAs(guestId))

    fun retry() = vm.onIntent(GuestEntryIntent.Retry)
}
