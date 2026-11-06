package app.plano.bridge

import app.plano.utilities.presentation.BaseViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What React sees of a view model: a state snapshot plus `subscribe`, the exact
 * shape `useSyncExternalStore` wants. Intents become plain methods on subclasses.
 */
@JsExport
abstract class Controller<S : Any> internal constructor(
    private val viewModel: BaseViewModel<S, *, *>,
) {
    private val scope = MainScope()

    // The store owns the view model's lifetime: clearing it cancels viewModelScope.
    private val store = ViewModelStore().also { it.put("vm", viewModel) }

    val state: S get() = viewModel.state.value

    /** Calls [onChange] after every new state; returns the unsubscribe function. */
    fun subscribe(onChange: () -> Unit): () -> Unit {
        val job = scope.launch { viewModel.state.collect { onChange() } }
        return { job.cancel() }
    }

    fun dispose() {
        scope.cancel()
        store.clear()
    }

    internal fun <E : Any> listen(viewModel: BaseViewModel<*, *, E>, listener: (E) -> Unit): () -> Unit {
        val job = scope.launch { viewModel.events.collect { listener(it) } }
        return { job.cancel() }
    }
}
