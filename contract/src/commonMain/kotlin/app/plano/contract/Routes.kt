package app.plano.contract

/** Paths relative to the API root. The web app reaches it at `/api/`. */
object Routes {
    const val ME = "me"
    const val LOGOUT = "auth/logout"
    const val EVENTS = "events"

    fun signIn(provider: String) = "auth/$provider"

    fun event(eventId: String) = "events/$eventId"

    fun join(eventId: String) = "events/$eventId/join"

    fun poll(eventId: String) = "events/$eventId/poll"

    fun pollOptions(eventId: String) = "events/$eventId/poll/options"

    fun vote(eventId: String, optionId: String) = "events/$eventId/poll/options/$optionId/vote"
}
