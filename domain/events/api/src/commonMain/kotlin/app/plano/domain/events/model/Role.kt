package app.plano.domain.events.model

enum class Role {
    OWNER,
    PLANNER,
    PARTICIPANT,
    ;

    val canPlan: Boolean get() = this != PARTICIPANT

    companion object {
        /** Friend groups: anyone who joins can plan (PRD, Domain model). */
        val DEFAULT_FOR_NEW_MEMBER = PLANNER

        fun fromName(name: String): Role? = entries.firstOrNull { it.name == name }
    }
}
