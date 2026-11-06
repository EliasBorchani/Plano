package app.plano.features.signin

sealed interface SignInIntent {
    data class SignInWithGoogle(val idToken: String) : SignInIntent

    data class SignInWithApple(val idToken: String, val displayName: String?) : SignInIntent

    data object SignOut : SignInIntent
}
