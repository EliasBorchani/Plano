package app.plano.server

data class Config(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    /** `dev` enables the fake Apple sign-in. */
    val appEnv: String,
    val googleClientId: String?,
    val appleServicesId: String?,
    /** False only for plain-http local development. */
    val cookieSecure: Boolean,
) {
    val isDev: Boolean get() = appEnv == "dev"

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun required(name: String) = checkNotNull(value(name)) { "$name is not set (see .env.example)" }
            return Config(
                port = value("PORT")?.toInt() ?: 8080,
                databaseUrl = value("DATABASE_URL") ?: "jdbc:postgresql://localhost:5434/plano",
                databaseUser = value("DATABASE_USER") ?: "plano",
                databasePassword = required("POSTGRES_PASSWORD"),
                appEnv = value("APP_ENV") ?: "production",
                googleClientId = value("GOOGLE_CLIENT_ID"),
                appleServicesId = value("APPLE_SERVICES_ID"),
                cookieSecure = value("COOKIE_SECURE")?.toBoolean() ?: true,
            )
        }
    }
}
