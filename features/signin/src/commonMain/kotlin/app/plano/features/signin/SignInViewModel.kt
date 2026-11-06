package app.plano.features.signin

import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.Session
import app.plano.domain.auth.repository.AuthRepository
import app.plano.utilities.kotlinext.errorKind
import app.plano.utilities.presentation.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide session: the UI shows sign-in or the app from [state]'s status. */
class SignInViewModel(
    private val auth: AuthRepository,
) : BaseViewModel<SignInState, SignInIntent, Nothing>() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    override val state: StateFlow<SignInState> = combine(auth.session, busy, error) { session, busy, error ->
        SignInState(
            status = when (session) {
                Session.Unknown -> SessionStatus.UNKNOWN
                Session.SignedOut -> SessionStatus.SIGNED_OUT
                is Session.SignedIn -> SessionStatus.SIGNED_IN
            },
            user = (session as? Session.SignedIn)?.user?.let { UserUi(id = it.id, displayName = it.displayName) },
            busy = busy,
            error = error,
        )
    }.stateIn(scope, SharingStarted.Eagerly, SignInState())

    init {
        run { auth.restore() }
    }

    override fun onIntent(intent: SignInIntent) {
        when (intent) {
            is SignInIntent.SignInWithGoogle -> run { auth.signIn(AuthProvider.GOOGLE, intent.idToken, null) }
            is SignInIntent.SignInWithApple -> run { auth.signIn(AuthProvider.APPLE, intent.idToken, intent.displayName) }
            SignInIntent.SignOut -> run { auth.signOut() }
        }
    }

    private fun run(block: suspend () -> Unit) {
        scope.launch {
            busy.value = true
            error.value = null
            runCatching { block() }.onFailure { error.value = it.errorKind.name }
            busy.value = false
        }
    }
}
