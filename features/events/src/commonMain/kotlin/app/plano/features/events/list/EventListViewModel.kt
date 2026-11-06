package app.plano.features.events.list

import app.plano.domain.events.repository.EventRepository
import app.plano.utilities.kotlinext.errorKind
import app.plano.utilities.presentation.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventListViewModel(
    private val eventRepository: EventRepository,
) : BaseViewModel<EventListState, EventListIntent, Nothing>() {
    private val error = MutableStateFlow<String?>(null)

    override val state: StateFlow<EventListState> = combine(eventRepository.observeMine(), error) { mine, error ->
        EventListState(
            loading = mine == null && error == null,
            items = mine.orEmpty().map { EventItemUi(id = it.id, title = it.title, type = it.type.name, role = it.myRole.name) },
            error = error,
        )
    }.stateIn(scope, SharingStarted.Eagerly, EventListState())

    init {
        refresh()
    }

    override fun onIntent(intent: EventListIntent) {
        when (intent) {
            EventListIntent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            error.value = null
            runCatching { eventRepository.refreshMine() }.onFailure { error.value = it.errorKind.name }
        }
    }
}
