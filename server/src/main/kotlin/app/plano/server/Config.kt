package app.plano.server

import java.time.Duration

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
    /** Signs access tokens. Null only in dev, where a random one is made at start. */
    val jwtSecret: ByteArray?,
    val accessTtl: Duration,
    val refreshTtl: Duration,
    /** The path under which browsers reach this server (the web proxy serves it at `/api`). */
    val publicApiPath: String,
) {
    val isDev: Boolean get() = appEnv == "dev"

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun required(name: String) = checkNotNull(value(name)) { "$name is not set (see .env.example)" }
            val appEnv = value("APP_ENV") ?: "production"
            return Config(
                port = value("PORT")?.toInt() ?: 8080,
                databaseUrl = value("DATABASE_URL") ?: "jdbc:postgresql://localhost:5434/plano",
                databaseUser = value("DATABASE_USER") ?: "plano",
                databasePassword = required("POSTGRES_PASSWORD"),
                appEnv = appEnv,
                googleClientId = value("GOOGLE_CLIENT_ID"),
                appleServicesId = value("APPLE_SERVICES_ID"),
                cookieSecure = value("COOKIE_SECURE")?.toBoolean() ?: true,
                jwtSecret = if (appEnv == "dev") value("JWT_SECRET")?.toByteArray() else required("JWT_SECRET").toByteArray(),
                accessTtl = value("ACCESS_TTL")?.let(::parseDuration) ?: Duration.ofMinutes(15),
                refreshTtl = value("REFRESH_TTL")?.let(::parseDuration) ?: Duration.ofDays(30),
                publicApiPath = value("PUBLIC_API_PATH") ?: "/api",
            )
        }
    }
}

/** `20s`, `15m`, `12h`, `30d`. */
fun parseDuration(text: String): Duration {
    val amount = text.dropLast(1).toLongOrNull()
    return when (text.lastOrNull()) {
        's' -> amount?.let(Duration::ofSeconds)
        'm' -> amount?.let(Duration::ofMinutes)
        'h' -> amount?.let(Duration::ofHours)
        'd' -> amount?.let(Duration::ofDays)
        else -> null
    } ?: error("Bad duration '$text': use a number and s, m, h or d (e.g. 15m)")
}
