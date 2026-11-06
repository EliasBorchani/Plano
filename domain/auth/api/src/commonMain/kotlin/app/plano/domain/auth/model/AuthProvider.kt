package app.plano.domain.auth.model

enum class AuthProvider(val wireName: String) {
    GOOGLE("google"),
    APPLE("apple"),
    ;

    companion object {
        fun fromWireName(name: String): AuthProvider? = entries.firstOrNull { it.wireName == name }
    }
}
