package app.plano.domain.events.model

/** Where a person stands in an event (owner, 2026-11-25). The RSVP is a separate answer. */
enum class MemberStatus {
    /** Added by a planner (by name, from a group, or by email) and has not opened the event yet. */
    INVITED,

    /** Opened the event at least once. Guests who come through the shared link start here. */
    JOINED,

    /** Left, or removed by a planner: sees only the title and type; the others see a « left » label. */
    LEFT,
    ;

    val isActive: Boolean get() = this != LEFT

    companion object {
        fun fromName(name: String): MemberStatus? = entries.firstOrNull { it.name == name }
    }
}

/** « I'm coming » or not, once the date and place are chosen. Not the same as a poll vote. */
enum class Rsvp {
    NOT_ANSWERED,
    COMING,
    NOT_COMING,
    ;

    companion object {
        fun fromName(name: String): Rsvp? = entries.firstOrNull { it.name == name }

        /**
         * Nothing is ever final (PRD, RSVP): when the chosen date or place changes, everyone goes back to
         * [NOT_ANSWERED]. A first choice is not a change: people may answer before the date is set.
         */
        fun resetsOnChange(previousChoice: String?, nextChoice: String?): Boolean =
            previousChoice != null && previousChoice != nextChoice
    }
}

/** One group picked in the invite step, after the untick modal. */
data class GroupPick(
    val groupId: String,
    val members: List<String>,
    val unticked: Set<String>,
)

object Invitees {
    /**
     * The people an invitation step adds: individuals plus each group's ticked members, each person once, without
     * the inviter and without people already in the event (PRD: a person in two selections is invited once).
     */
    fun resolve(
        individuals: List<String>,
        groups: List<GroupPick>,
        inviter: String,
        alreadyMembers: Set<String>,
    ): List<String> =
        (individuals + groups.flatMap { pick -> pick.members.filterNot { it in pick.unticked } })
            .distinct()
            .filterNot { it == inviter || it in alreadyMembers }
}

object EmailAddress {
    const val MAX = 254

    fun normalize(raw: String): String = raw.trim().lowercase()

    /** A sanity check, not RFC 5322: one `@`, something before it, a dot in the domain, no spaces. */
    fun isValid(raw: String): Boolean {
        val email = normalize(raw)
        if (email.length > MAX || email.any { it.isWhitespace() }) return false
        val local = email.substringBefore('@', "")
        val domain = email.substringAfter('@', "")
        return local.isNotEmpty() && '@' !in domain && '.' in domain.trim('.') && !domain.startsWith('.') &&
            !domain.endsWith('.')
    }

    /** The name shown for someone invited by email until they choose one: `sara.martin@x.fr` → `sara.martin`. */
    fun defaultName(raw: String): String = normalize(raw).substringBefore('@')
}
