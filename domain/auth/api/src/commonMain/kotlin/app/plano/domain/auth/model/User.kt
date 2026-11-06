package app.plano.domain.auth.model

data class User(
    val id: String,
    val displayName: String,
    val email: String?,
)
