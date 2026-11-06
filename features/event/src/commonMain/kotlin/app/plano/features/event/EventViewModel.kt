package app.plano.features.event

import app.plano.domain.auth.model.Session
import app.plano.domain.auth.repository.AuthRepository
import app.plano.domain.events.model.Event
import app.plano.domain.events.repository.EventRepository
import app.plano.domain.polls.model.DatePoll
import app.plano.domain.polls.model.PollTally
import app.plano.domain.polls.model.VoteChoice
import app.plano.domain.polls.repository.PollRepository
import app.plano.utilities.kotlinext.AppError
import app.plano.utilities.kotlinext.errorKind
import app.plano.utilities.presentation.BaseViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** The event page: its details and its date poll (two contexts, combined here). */
class EventViewModel(
    private val eventId: String,
    /** True when opened from a shared link: joins the event before loading it. */
    private val joinOnOpen: Boolean,
    private val eventRepository: EventRepository,
    private val pollRepository: PollRepository,
    auth: AuthRepository,
    private val today: Today = SystemToday,
) : BaseViewModel<EventState, EventIntent, Nothing>() {
    private val newDate = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val loadFailure = MutableStateFlow<AppError.Kind?>(null)

    private val ui = combine(newDate, busy, error, loadFailure) { newDate, busy, error, loadFailure ->
        UiFlags(newDate, busy, error, loadFailure)
    }

    override val state: StateFlow<EventState> = combine(
        eventRepository.observe(eventId),
        pollRepository.observe(eventId),
        auth.session,
        ui,
    ) { event, poll, session, ui ->
        buildState(event, poll, (session as? Session.SignedIn)?.user?.id, ui)
    }.stateIn(scope, SharingStarted.Eagerly, EventState())

    init {
        scope.launch {
            runCatching {
                if (joinOnOpen) eventRepository.join(eventId)
                load()
            }.onFailure { loadFailure.value = it.errorKind }
        }
    }

    override fun onIntent(intent: EventIntent) {
        when (intent) {
            EventIntent.Refresh -> mutate { load() }
            is EventIntent.SetNewDate -> newDate.value = intent.isoDate
            EventIntent.AddDate -> addDate()
            is EventIntent.Vote -> vote(intent.optionId, intent.choice)
        }
    }

    private suspend fun load() {
        val event = scope.async { eventRepository.refresh(eventId) }
        val poll = scope.async { pollRepository.refresh(eventId) }
        event.await()
        poll.await()
    }

    private fun addDate() {
        val date = runCatching { LocalDate.parse(newDate.value) }.getOrNull()
        val poll = state.value
        val problem = when {
            date == null -> AddDateError.DATE_INVALID
            date < today.date() -> AddDateError.DATE_IN_PAST
            poll.options.any { it.date == date.toString() } -> AddDateError.DATE_ALREADY_OFFERED
            else -> null
        }
        if (problem != null || date == null) {
            error.value = problem?.name
            return
        }
        mutate {
            pollRepository.addDate(eventId, date)
            newDate.value = ""
        }
    }

    private fun vote(optionId: String, choice: VoteChoice) {
        val current = state.value.options.firstOrNull { it.id == optionId }?.myChoice
        mutate { pollRepository.vote(eventId, optionId, if (current == choice.name) null else choice) }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (busy.value) return
        scope.launch {
            busy.value = true
            error.value = null
            runCatching { block() }.onFailure { error.value = it.errorKind.name }
            busy.value = false
        }
    }

    private fun buildState(event: Event?, poll: DatePoll?, myUserId: String?, ui: UiFlags): EventState {
        if (event == null || poll == null) {
            return EventState(
                loading = ui.loadFailure == null,
                notFound = ui.loadFailure == AppError.Kind.NOT_FOUND,
                error = ui.loadFailure?.takeIf { it != AppError.Kind.NOT_FOUND }?.name,
            )
        }
        val tally = PollTally.tally(poll)
        val dates = poll.options.associateBy { it.id }
        return EventState(
            loading = false,
            title = event.title,
            type = event.type.name,
            description = event.description,
            canPlan = event.myRole.canPlan,
            members = event.members.map {
                MemberUi(userId = it.userId, displayName = it.displayName, role = it.role.name, isMe = it.userId == myUserId)
            },
            options = tally.options.map { option ->
                DateOptionUi(
                    id = option.optionId,
                    date = dates.getValue(option.optionId).date.toString(),
                    prefer = option.prefer,
                    cannot = option.cannot,
                    ifMajority = option.ifMajority,
                    myChoice = myUserId?.let { poll.myChoice(option.optionId, it)?.name },
                    leadingOnPrefer = option.optionId == tally.leaderOnPrefer,
                    leadingWithIfMajority = option.optionId == tally.leaderWithIfMajority,
                )
            },
            newDate = ui.newDate,
            choices = VoteChoice.entries.map { it.name },
            busy = ui.busy,
            error = ui.error,
        )
    }

    private data class UiFlags(
        val newDate: String,
        val busy: Boolean,
        val error: String?,
        val loadFailure: AppError.Kind?,
    )
}
