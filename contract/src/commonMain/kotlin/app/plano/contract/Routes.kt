package app.plano.contract

/** Paths relative to the API root. The web app reaches it at `/api/`. */
object Routes {
    const val ME = "me"
    const val LOGOUT = "auth/logout"
    const val REFRESH = "auth/refresh"

    /** Auth routes never trigger a refresh-and-retry in the client. */
    const val AUTH_PREFIX = "auth/"
    const val EVENTS = "events"
    const val LINK_PREVIEW = "links/preview"
    const val PLACES = "places"
    const val PLACE_SEARCH = "places/search"
    const val PLACE_FROM_LINK = "places/from-link"

    fun signIn(provider: String) = "auth/$provider"

    /** GET: the public invite. POST: join as a new guest. Under `auth/` so the browser sends the refresh cookie. */
    fun guestInvite(eventId: String) = "auth/guest/$eventId"

    fun continueAsGuest(eventId: String, guestId: String) = "auth/guest/$eventId/as/$guestId"

    fun event(eventId: String) = "events/$eventId"

    fun join(eventId: String) = "events/$eventId/join"

    fun poll(eventId: String) = "events/$eventId/poll"

    fun pollOptions(eventId: String) = "events/$eventId/poll/options"

    fun vote(eventId: String, optionId: String) = "events/$eventId/poll/options/$optionId/vote"
}
