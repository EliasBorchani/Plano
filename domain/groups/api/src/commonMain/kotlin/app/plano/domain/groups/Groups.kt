package app.plano.domain.groups

/**
 * A group is a named set of people joined through an invite link (PRD, Domain model). It is only a shortcut to
 * select many people: events never belong to a group, they keep the group as context.
 */
object GroupName {
    const val MAX = 60

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(raw: String): GroupError? {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> GroupError.NAME_MISSING
            name.length > MAX -> GroupError.NAME_TOO_LONG
            else -> null
        }
    }
}

/**
 * The group's one current invite link. Every member manages it (owner, 2026-11-25): change the limits, or make a
 * new link, which kills the old one.
 */
data class InviteLimits(
    val maxUses: Int = DEFAULT_MAX_USES,
    val expiresInDays: Int = DEFAULT_EXPIRES_IN_DAYS,
) {
    fun validate(): GroupError? = when {
        maxUses !in 1..MAX_USES_LIMIT -> GroupError.MAX_USES_OUT_OF_RANGE
        expiresInDays !in 1..EXPIRY_LIMIT_DAYS -> GroupError.EXPIRY_OUT_OF_RANGE
        else -> null
    }

    companion object {
        /** PRD, V1 scope: a link has a user limit (default 10) and an expiry (default 7 days). */
        const val DEFAULT_MAX_USES = 10
        const val DEFAULT_EXPIRES_IN_DAYS = 7
        const val MAX_USES_LIMIT = 100
        const val EXPIRY_LIMIT_DAYS = 30
    }
}

enum class GroupError {
    NAME_MISSING,
    NAME_TOO_LONG,
    MAX_USES_OUT_OF_RANGE,
    EXPIRY_OUT_OF_RANGE,

    /** Joining through a link that expired, was replaced, or reached its limit. */
    INVITE_EXPIRED,
    INVITE_FULL,
}
