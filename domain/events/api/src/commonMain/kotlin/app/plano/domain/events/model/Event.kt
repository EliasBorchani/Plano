package app.plano.domain.events.model

data class EventSummary(
    val id: String,
    val title: String,
    val type: EventType,
    val myRole: Role,
)

data class Event(
    val id: String,
    val title: String,
    val type: EventType,
    val description: String?,
    val myRole: Role,
    val members: List<Member>,
)

data class Member(
    val userId: String,
    val displayName: String,
    val role: Role,
)
