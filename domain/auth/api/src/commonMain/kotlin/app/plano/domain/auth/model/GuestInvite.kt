package app.plano.domain.auth.model

/** What an event link shows before anyone signs in: the event, and its guests to continue as. */
data class GuestInvite(
    val eventId: String,
    val eventTitle: String,
    /** `EventType` name: the auth context does not depend on the events one. */
    val eventType: String,
    val guests: List<Guest>,
)

data class Guest(
    val id: String,
    val displayName: String,
)
