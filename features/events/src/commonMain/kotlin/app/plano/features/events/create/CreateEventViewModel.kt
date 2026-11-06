package app.plano.features.events.create

import app.plano.domain.events.model.EventType
import app.plano.domain.events.model.NewEvent
import app.plano.domain.events.repository.EventRepository
import app.plano.utilities.kotlinext.errorKind
import app.plano.utilities.presentation.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateEventViewModel(
    private val eventRepository: EventRepository,
) : BaseViewModel<CreateEventState, CreateEventIntent, CreateEventEvent>() {
    private val draft = MutableStateFlow(NewEvent(title = "", type = EventType.DINNER, description = null))
    private val submitted = MutableStateFlow(false)
    private val submitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val types = EventType.entries.map { it.name }

    override val state: StateFlow<CreateEventState> = combine(draft, submitted, submitting, error) { draft, submitted, submitting, error ->
        CreateEventState(
            title = draft.title,
            type = draft.type.name,
            description = draft.description.orEmpty(),
            types = types,
            errors = if (submitted) draft.validate().map { it.name } else emptyList(),
            submitting = submitting,
            error = error,
        )
    }.stateIn(scope, SharingStarted.Eagerly, CreateEventState(type = EventType.DINNER.name, types = types))

    override fun onIntent(intent: CreateEventIntent) {
        when (intent) {
            is CreateEventIntent.SetTitle -> draft.update { it.copy(title = intent.value) }
            is CreateEventIntent.SetType -> EventType.fromName(intent.value)?.let { type -> draft.update { it.copy(type = type) } }
            is CreateEventIntent.SetDescription -> draft.update { it.copy(description = intent.value) }
            CreateEventIntent.Submit -> submit()
        }
    }

    private fun submit() {
        submitted.value = true
        val event = draft.value.normalized()
        if (event.validate().isNotEmpty() || submitting.value) return
        scope.launch {
            submitting.value = true
            error.value = null
            runCatching { eventRepository.create(event) }
                .onSuccess { emitEvent(CreateEventEvent.Created(it)) }
                .onFailure { error.value = it.errorKind.name }
            submitting.value = false
        }
    }
}
