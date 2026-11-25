package app.plano.domain.auth.model

/** The only thing a guest types. [validate] runs in every client and again on the server. */
object GuestName {
    const val MAX = 40

    fun normalize(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    fun validate(name: String): GuestNameError? {
        val normalized = normalize(name)
        return when {
            normalized.isEmpty() -> GuestNameError.NAME_MISSING
            normalized.length > MAX -> GuestNameError.NAME_TOO_LONG
            else -> null
        }
    }

    /** Two members of one event may not share a name, so « C'est moi » always points at one person. */
    fun sameName(a: String, b: String): Boolean = normalize(a).equals(normalize(b), ignoreCase = true)
}

enum class GuestNameError {
    NAME_MISSING,
    NAME_TOO_LONG,

    /** Found by the server: another member of the event already has this name. */
    NAME_TAKEN,
}
