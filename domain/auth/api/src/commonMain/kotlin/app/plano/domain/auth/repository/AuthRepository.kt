package app.plano.domain.auth.repository

import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.Session
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<Session>

    /** Asks the server who the session cookie belongs to. */
    suspend fun restore()

    /** [idToken] is the JWT the provider's own sign-in button returned to the UI. */
    suspend fun signIn(provider: AuthProvider, idToken: String, displayName: String?)

    suspend fun signOut()
}
