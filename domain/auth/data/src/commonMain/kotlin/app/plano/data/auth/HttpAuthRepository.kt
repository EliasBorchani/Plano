package app.plano.data.auth

import app.plano.contract.GuestInviteDto
import app.plano.contract.JoinAsGuestRequest
import app.plano.contract.Routes
import app.plano.contract.SignInRequest
import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.Guest
import app.plano.domain.auth.model.GuestInvite
import app.plano.domain.auth.model.Session
import app.plano.domain.auth.model.User
import app.plano.domain.auth.repository.AuthRepository
import app.plano.utilities.kotlinext.AppError
import app.plano.utilities.network.SessionEvents
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tokens are httpOnly cookies set by the server: this class never sees one. The HTTP client refreshes them on
 * its own and reports through [sessionEvents] when it no longer can.
 */
class HttpAuthRepository(
    private val client: HttpClient,
    sessionEvents: SessionEvents,
) : AuthRepository {
    private val _session = MutableStateFlow<Session>(Session.Unknown)
    override val session: StateFlow<Session> = _session.asStateFlow()

    init {
        sessionEvents.onExpired { _session.value = Session.SignedOut }
    }

    override suspend fun restore() {
        _session.value = try {
            Session.SignedIn(client.get(Routes.ME).body<UserDto>().toDomain())
        } catch (e: AppError) {
            if (e.kind != AppError.Kind.UNAUTHORIZED) throw e
            Session.SignedOut
        }
    }

    override suspend fun signIn(provider: AuthProvider, idToken: String, displayName: String?) {
        val user = client.post(Routes.signIn(provider.wireName)) {
            setBody(SignInRequest(idToken = idToken, displayName = displayName))
        }.body<UserDto>()
        _session.value = Session.SignedIn(user.toDomain())
    }

    override suspend fun signOut() {
        client.post(Routes.LOGOUT)
        _session.value = Session.SignedOut
    }

    override suspend fun guestInvite(eventId: String): GuestInvite {
        val dto = client.get(Routes.guestInvite(eventId)).body<GuestInviteDto>()
        return GuestInvite(
            eventId = dto.eventId,
            eventTitle = dto.title,
            eventType = dto.type,
            guests = dto.guests.map { Guest(id = it.id, displayName = it.displayName) },
        )
    }

    override suspend fun joinAsGuest(eventId: String, displayName: String) {
        val user = client.post(Routes.guestInvite(eventId)) {
            setBody(JoinAsGuestRequest(displayName))
        }.body<UserDto>()
        _session.value = Session.SignedIn(user.toDomain())
    }

    override suspend fun continueAsGuest(eventId: String, guestId: String) {
        val user = client.post(Routes.continueAsGuest(eventId, guestId)).body<UserDto>()
        _session.value = Session.SignedIn(user.toDomain())
    }
}

private fun UserDto.toDomain() = User(id = id, displayName = displayName, email = email, guestEventId = guestEventId)
