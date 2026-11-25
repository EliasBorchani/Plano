package app.plano.domain.auth.repository

import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.GuestInvite
import app.plano.domain.auth.model.Session
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<Session>

    /** Asks the server who the session cookie belongs to. */
    suspend fun restore()

    /**
     * [idToken] is the JWT the provider's own sign-in button returned to the UI.
     * Signed in as a guest, the guest's votes and membership move into the account.
     */
    suspend fun signIn(provider: AuthProvider, idToken: String, displayName: String?)

    suspend fun signOut()

    /** Needs no session: anyone with the event link may see it. */
    suspend fun guestInvite(eventId: String): GuestInvite

    /** Creates a guest of [eventId] with only a name, and signs in as that guest. */
    suspend fun joinAsGuest(eventId: String, displayName: String)

    /** Signs in as an existing guest of [eventId] (same person on another device). */
    suspend fun continueAsGuest(eventId: String, guestId: String)
}
