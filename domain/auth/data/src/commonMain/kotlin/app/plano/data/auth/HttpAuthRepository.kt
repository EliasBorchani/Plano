package app.plano.data.auth

import app.plano.contract.Routes
import app.plano.contract.SignInRequest
import app.plano.contract.UserDto
import app.plano.domain.auth.model.AuthProvider
import app.plano.domain.auth.model.Session
import app.plano.domain.auth.model.User
import app.plano.domain.auth.repository.AuthRepository
import app.plano.utilities.kotlinext.AppError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The session itself is an httpOnly cookie set by the server: this class never sees a token after sign-in. */
class HttpAuthRepository(
    private val client: HttpClient,
) : AuthRepository {
    private val _session = MutableStateFlow<Session>(Session.Unknown)
    override val session: StateFlow<Session> = _session.asStateFlow()

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
}

private fun UserDto.toDomain() = User(id = id, displayName = displayName, email = email)
