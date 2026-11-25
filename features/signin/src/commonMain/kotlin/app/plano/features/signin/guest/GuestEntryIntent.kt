package app.plano.features.signin.guest

sealed interface GuestEntryIntent {
    data class SetName(val value: String) : GuestEntryIntent

    data object Join : GuestEntryIntent

    data class ContinueAs(val guestId: String) : GuestEntryIntent

    data object Retry : GuestEntryIntent
}
