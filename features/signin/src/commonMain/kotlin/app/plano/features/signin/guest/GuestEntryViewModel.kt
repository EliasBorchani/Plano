package app.plano.features.signin.guest

import app.plano.domain.auth.model.GuestInvite
import app.plano.domain.auth.model.GuestName
import app.plano.domain.auth.model.GuestNameError
import app.plano.domain.auth.repository.AuthRepository
import app.plano.utilities.kotlinext.AppError
import app.plano.utilities.kotlinext.errorKind
import app.plano.utilities.presentation.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Joining an event from its link with only a name (PRD: signing in is never required to take part).
 * On success the app-wide session becomes the guest, and the UI shows the event.
 */
class GuestEntryViewModel(
    private val eventId: String,
    private val auth: AuthRepository,
) : BaseViewModel<GuestEntryState, GuestEntryIntent, Nothing>() {
    private val invite = MutableStateFlow<GuestInvite?>(null)
    private val loadFailure = MutableStateFlow<AppError.Kind?>(null)
    private val name = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    override val state: StateFlow<GuestEntryState> = combine(invite, loadFailure, name, busy, error) { invite, loadFailure, name, busy, error ->
        GuestEntryState(
            loading = invite == null && loadFailure == null,
            notFound = loadFailure == AppError.Kind.NOT_FOUND,
            eventTitle = invite?.eventTitle.orEmpty(),
            eventType = invite?.eventType.orEmpty(),
            guests = invite?.guests.orEmpty().map { GuestUi(id = it.id, displayName = it.displayName) },
            name = name,
            busy = busy,
            error = error ?: loadFailure?.takeIf { it != AppError.Kind.NOT_FOUND }?.name,
        )
    }.stateIn(scope, SharingStarted.Eagerly, GuestEntryState())

    init {
        load()
    }

    override fun onIntent(intent: GuestEntryIntent) {
        when (intent) {
            is GuestEntryIntent.SetName -> {
                name.value = intent.value
                error.value = null
            }
            GuestEntryIntent.Join -> join()
            is GuestEntryIntent.ContinueAs -> mutate { auth.continueAsGuest(eventId, intent.guestId) }
            GuestEntryIntent.Retry -> load()
        }
    }

    private fun load() {
        loadFailure.value = null
        scope.launch {
            runCatching { invite.value = auth.guestInvite(eventId) }.onFailure { loadFailure.value = it.errorKind }
        }
    }

    private fun join() {
        val problem = GuestName.validate(name.value)
            // Guests are listed: the same name would be refused by the server, the list points to « C'est moi ».
            ?: GuestNameError.NAME_TAKEN.takeIf { invite.value?.guests.orEmpty().any { GuestName.sameName(it.displayName, name.value) } }
        if (problem != null) {
            error.value = problem.name
            return
        }
        mutate(onConflict = GuestNameError.NAME_TAKEN.name) { auth.joinAsGuest(eventId, GuestName.normalize(name.value)) }
    }

    private fun mutate(onConflict: String? = null, block: suspend () -> Unit) {
        if (busy.value) return
        scope.launch {
            busy.value = true
            error.value = null
            runCatching { block() }.onFailure {
                error.value = if (it.errorKind == AppError.Kind.CONFLICT && onConflict != null) onConflict else it.errorKind.name
            }
            busy.value = false
        }
    }
}
