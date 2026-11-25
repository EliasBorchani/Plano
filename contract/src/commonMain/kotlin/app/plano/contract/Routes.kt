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
    const val GROUPS = "groups"
    const val USER_SEARCH = "users/search"

    fun signIn(provider: String) = "auth/$provider"

    /** GET: the public invite. POST: join as a new guest. Under `auth/` so the browser sends the refresh cookie. */
    fun guestInvite(eventId: String) = "auth/guest/$eventId"

    fun continueAsGuest(eventId: String, guestId: String) = "auth/guest/$eventId/as/$guestId"

    fun event(eventId: String) = "events/$eventId"

    fun join(eventId: String) = "events/$eventId/join"

    fun poll(eventId: String) = "events/$eventId/poll"

    fun pollOptions(eventId: String) = "events/$eventId/poll/options"

    fun vote(eventId: String, optionId: String) = "events/$eventId/poll/options/$optionId/vote"

    /** GET: preview. POST: sign in as the person invited by email (their personal link). */
    fun invitation(token: String) = "auth/invite/$token"

    fun invitations(eventId: String) = "events/$eventId/invitations"

    fun member(eventId: String, userId: String) = "events/$eventId/members/$userId"

    fun rsvp(eventId: String) = "events/$eventId/rsvp"

    fun archived(eventId: String) = "events/$eventId/archived"

    /** `kind`: `date` or `place`. */
    fun pollOf(eventId: String, kind: String) = "events/$eventId/polls/$kind"

    fun pollOptionsOf(eventId: String, kind: String) = "events/$eventId/polls/$kind/options"

    fun voteOf(eventId: String, kind: String, optionId: String) = "events/$eventId/polls/$kind/options/$optionId/vote"

    fun deadline(eventId: String, kind: String) = "events/$eventId/polls/$kind/deadline"

    fun validated(eventId: String, kind: String) = "events/$eventId/polls/$kind/validated"

    fun group(groupId: String) = "groups/$groupId"

    fun groupInvite(groupId: String) = "groups/$groupId/invite"

    fun groupMember(groupId: String, userId: String) = "groups/$groupId/members/$userId"

    fun groupEvents(groupId: String) = "groups/$groupId/events"

    /** GET: public preview. POST: join (accounts only). */
    fun groupJoin(token: String) = "group-invites/$token"
}
