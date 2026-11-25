package app.plano.server

import app.plano.contract.AddOptionRequest
import app.plano.contract.ArchiveRequest
import app.plano.contract.CreateEventRequest
import app.plano.contract.CreateGroupRequest
import app.plano.contract.CreatedDto
import app.plano.contract.DeadlineRequest
import app.plano.contract.EmailInviteDto
import app.plano.contract.EventDto
import app.plano.contract.EventSummaryDto
import app.plano.contract.GroupDto
import app.plano.contract.GroupPickDto
import app.plano.contract.GuestInviteDto
import app.plano.contract.InvitationPreviewDto
import app.plano.contract.InvitationsDto
import app.plano.contract.InviteesRequest
import app.plano.contract.LinkOptionDto
import app.plano.contract.PersonDto
import app.plano.contract.PollDto
import app.plano.contract.RsvpRequest
import app.plano.contract.UpdateGroupInviteRequest
import app.plano.contract.UpdateProfileRequest
import app.plano.contract.UserDto
import app.plano.contract.ValidateRequest
import app.plano.contract.VoteRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataModelTest {
    private suspend inline fun <reified T : Any> HttpClient.postJson(path: String, body: T): HttpResponse =
        post(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend inline fun <reified T : Any> HttpClient.putJson(path: String, body: T): HttpResponse =
        put(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpClient.createGroup(name: String): GroupDto {
        val id = postJson("/groups", CreateGroupRequest(name)).body<CreatedDto>().id
        return get("/groups/$id").body()
    }

    private suspend fun HttpClient.createEventWith(invitees: InviteesRequest): String =
        postJson("/events", CreateEventRequest(title = "Dîner", type = "DINNER", invitees = invitees)).body<CreatedDto>().id

    private suspend fun HttpClient.members(eventId: String) =
        get("/events/$eventId").body<EventDto>().members.associate { it.displayName to it.status }

    private suspend fun HttpClient.code(response: HttpResponse): String = response.body<app.plano.contract.ErrorDto>().code

    @Test
    fun groupLinksHaveALimitAnExpiryAndCanBeReplaced() = api(clock = TestClock()) {
        val lea = user().also { it.signIn("google", "lea") }
        val group = lea.createGroup("  Escalade  ")
        assertEquals("Escalade", group.name)
        assertEquals(10, group.invite.maxUses)
        assertEquals("2026-11-13T12:00:00Z", group.invite.expiresAt)

        val small = lea.putJson("/groups/${group.id}/invite", UpdateGroupInviteRequest(maxUses = 1, expiresInDays = 7))
            .body<GroupDto>()
        assertEquals(group.invite.token, small.invite.token)

        val max = user().also { it.signIn("google", "max") }
        assertEquals("Escalade", max.get("/group-invites/${group.invite.token}").body<app.plano.contract.GroupInvitePreviewDto>().name)
        assertEquals(HttpStatusCode.OK, max.post("/group-invites/${group.invite.token}").status)
        assertEquals(HttpStatusCode.OK, max.post("/group-invites/${group.invite.token}").status, "joining twice is fine")

        val sara = user().also { it.signIn("google", "sara") }
        val full = sara.post("/group-invites/${group.invite.token}")
        assertEquals(HttpStatusCode.Gone, full.status)
        assertEquals("INVITE_FULL", sara.code(full))

        // Every member manages the link: Max makes a new one, the old one dies.
        val fresh = max.putJson("/groups/${group.id}/invite", UpdateGroupInviteRequest(5, 7, newLink = true)).body<GroupDto>()
        assertEquals(HttpStatusCode.NotFound, sara.post("/group-invites/${group.invite.token}").status)
        assertEquals(HttpStatusCode.OK, sara.post("/group-invites/${fresh.invite.token}").status)
        assertEquals(listOf("Lea", "Max", "Sara"), lea.get("/groups/${group.id}").body<GroupDto>().members.map { it.displayName })

        // Any member removes anyone.
        val saraId = sara.get("/me").body<UserDto>().id
        assertEquals(HttpStatusCode.NoContent, max.delete("/groups/${group.id}/members/$saraId").status)
        assertEquals(HttpStatusCode.NotFound, sara.get("/groups/${group.id}").status)
    }

    @Test
    fun anExpiredGroupLinkIsRefusedAndGuestsCannotJoinGroups() {
        val clock = TestClock()
        api(clock = clock) {
            val lea = user().also { it.signIn("google", "lea") }
            val group = lea.createGroup("Escalade")
            val eventId = lea.createEvent()
            val guest = user().also { it.joinAsGuest(eventId, "Nina") }
            assertEquals(HttpStatusCode.Forbidden, guest.post("/group-invites/${group.invite.token}").status)
            assertEquals(HttpStatusCode.Forbidden, guest.postJson("/groups", CreateGroupRequest("Moi")).status)

            clock.advance(Duration.ofDays(8))
            val max = user().also { it.signIn("google", "max") }
            val expired = max.post("/group-invites/${group.invite.token}")
            assertEquals(HttpStatusCode.Gone, expired.status)
            assertEquals("INVITE_EXPIRED", max.code(expired))
        }
    }

    @Test
    fun anEventInvitesIndividualsGroupsAndEmailsOnceEach() = api(clock = TestClock()) {
        val lea = user().also { it.signIn("google", "lea") }
        val group = lea.createGroup("Escalade")
        val people = listOf("max", "sara", "tom").associateWith { name ->
            user().also { it.signIn("google", name); it.post("/group-invites/${group.invite.token}") }
        }
        val ids = people.mapValues { (_, client) -> client.get("/me").body<UserDto>().id }

        val eventId = lea.createEventWith(
            InviteesRequest(
                userIds = listOf(ids.getValue("max")),
                groups = listOf(GroupPickDto(group.id, untickedUserIds = listOf(ids.getValue("tom")))),
                emails = listOf(EmailInviteDto("Omar@Example.fr"), EmailInviteDto("max@example.com")),
            ),
        )

        // Max is picked three times (by name, in the group, by his account's email) and invited once.
        assertEquals(
            mapOf("Lea" to "JOINED", "Max" to "INVITED", "Sara" to "INVITED", "omar" to "INVITED"),
            lea.members(eventId),
        )
        assertEquals(listOf("Escalade"), lea.get("/events/$eventId").body<EventDto>().groups.map { it.name })

        // Opening the event is joining; Tom, unticked, does not see it, not even through the group.
        assertEquals("JOINED", people.getValue("max").get("/events/$eventId").body<EventDto>().myStatus)
        assertEquals(HttpStatusCode.NotFound, people.getValue("tom").get("/events/$eventId").status)
        assertEquals(emptyList(), people.getValue("tom").get("/groups/${group.id}/events").body<List<EventSummaryDto>>())
        assertEquals(listOf(eventId), people.getValue("sara").get("/groups/${group.id}/events").body<List<EventSummaryDto>>().map { it.id })
    }

    @Test
    fun onlyKnownPeopleCanBeInvitedById() = api {
        val lea = user().also { it.signIn("google", "lea") }
        val stranger = user().also { it.signIn("google", "stranger") }
        val strangerId = stranger.get("/me").body<UserDto>().id

        val refused = lea.postJson("/events", CreateEventRequest("Dîner", "DINNER", invitees = InviteesRequest(userIds = listOf(strangerId))))
        assertEquals("INVITEE_UNKNOWN", lea.code(refused))
        assertTrue(stranger.get("/events").body<List<EventSummaryDto>>().isEmpty(), "nothing was created")

        // Once searchable, the stranger can be found by name and invited.
        assertTrue(lea.get("/users/search?q=stran").body<List<PersonDto>>().isEmpty())
        assertTrue(stranger.patchProfile(searchable = true).searchable)
        assertEquals(listOf("Stranger"), lea.get("/users/search?q=STRAN").body<List<PersonDto>>().map { it.displayName })
        assertEquals(HttpStatusCode.Created, lea.postJson("/events", CreateEventRequest("Dîner", "DINNER", invitees = InviteesRequest(userIds = listOf(strangerId)))).status)
    }

    private suspend fun HttpClient.patchProfile(searchable: Boolean): UserDto =
        patch("/me") { contentType(ContentType.Application.Json); setBody(UpdateProfileRequest(searchable)) }.body()

    @Test
    fun aPersonalLinkSignsTheEmailInviteeInAndStaysOutOfCestMoi() = api(clock = TestClock()) {
        val lea = user().also { it.signIn("google", "lea") }
        val eventId = lea.createEventWith(InviteesRequest(emails = listOf(EmailInviteDto("sara@example.fr", "Sara"))))
        val link = lea.get("/events/$eventId/invitations").body<InvitationsDto>().links.single()
        assertEquals("sara@example.fr", link.email)

        assertTrue(user().get("/auth/guest/$eventId").body<GuestInviteDto>().guests.isEmpty())
        assertEquals(HttpStatusCode.NotFound, user().post("/auth/guest/$eventId/as/${link.userId}").status)

        val phone = user()
        assertEquals("Sara", phone.get("/auth/invite/${link.token}").body<InvitationPreviewDto>().displayName)
        assertEquals(link.userId, phone.post("/auth/invite/${link.token}").body<UserDto>().id)
        assertEquals("JOINED", phone.get("/events/$eventId").body<EventDto>().myStatus)

        // Inviting the same email again gives the same person and link.
        val again = lea.postJson("/events/$eventId/invitations", InviteesRequest(emails = listOf(EmailInviteDto("SARA@example.fr"))))
            .body<InvitationsDto>()
        assertEquals(link, again.links.single())

        // Once Sara makes an account, the link no longer signs anyone in.
        phone.signIn("google", "sara")
        assertEquals(HttpStatusCode.NotFound, user().post("/auth/invite/${link.token}").status)
    }

    @Test
    fun leavingKeepsTheTitleOnlyAndAPlannerBringsYouBack() = api(clock = TestClock()) {
        val lea = user().also { it.signIn("google", "lea") }
        val max = user().also { it.signIn("google", "max") }
        val maxId = max.get("/me").body<UserDto>().id
        val eventId = lea.createEvent()
        max.post("/events/$eventId/join")
        val date = lea.postJson("/events/$eventId/polls/date/options", AddOptionRequest(date = "2026-12-05")).body<PollDto>()
        max.putJson("/events/$eventId/polls/date/options/${date.options.single().id}/vote", VoteRequest("PREFER"))

        assertEquals(HttpStatusCode.Conflict, lea.delete("/events/$eventId/members/${lea.get("/me").body<UserDto>().id}").status)
        assertEquals(HttpStatusCode.NoContent, max.delete("/events/$eventId/members/$maxId").status)

        val seen = max.get("/events/$eventId").body<EventDto>()
        assertEquals("LEFT", seen.myStatus)
        assertEquals("Dîner", seen.title)
        assertTrue(seen.members.isEmpty())
        assertEquals(HttpStatusCode.Forbidden, max.get("/events/$eventId/polls/date").status)
        assertEquals(HttpStatusCode.Forbidden, max.post("/events/$eventId/join").status, "the link does not bring you back")
        assertEquals("LEFT", lea.members(eventId)["Max"])
        assertTrue(lea.get("/events/$eventId/polls/date").body<PollDto>().votes.isEmpty(), "their votes no longer count")

        lea.postJson("/events/$eventId/invitations", InviteesRequest(userIds = listOf(maxId)))
        assertEquals("INVITED", lea.members(eventId)["Max"])
    }

    @Test
    fun thePlacePollTakesPlacesAndLinks() = api(clock = TestClock()) {
        val lea = user().also { it.signIn("google", "lea") }
        val eventId = lea.createEvent()
        val poll = lea.get("/events/$eventId/polls/place").body<PollDto>()
        assertEquals("PLACE", poll.kind)
        assertNull(poll.deadline)

        val withLink = lea.postJson(
            "/events/$eventId/polls/place/options",
            AddOptionRequest(link = LinkOptionDto("https://chez-paul.example.fr/?utm_source=wa", " Chez Paul ")),
        ).body<PollDto>()
        assertEquals(LinkOptionDto("https://chez-paul.example.fr", "Chez Paul"), withLink.options.single().link)

        val duplicate = lea.postJson("/events/$eventId/polls/place/options", AddOptionRequest(link = LinkOptionDto("https://chez-paul.example.fr", "Paul")))
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("OPTION_INVALID", lea.code(lea.postJson("/events/$eventId/polls/place/options", AddOptionRequest(date = "2026-12-05"))))
        assertEquals("PLACE_UNKNOWN", lea.code(lea.postJson("/events/$eventId/polls/place/options", AddOptionRequest(placeId = "00000000-0000-0000-0000-000000000000"))))
        assertEquals(HttpStatusCode.NotFound, lea.get("/events/$eventId/polls/venue").status)
    }

    @Test
    fun theFirstOptionStartsThePollWithADeadlineAndReminders() {
        val clock = TestClock()
        apiWithDb(clock = clock) { db ->
            val lea = user().also { it.signIn("google", "lea") }
            val eventId = lea.createEvent()
            val poll = lea.postJson("/events/$eventId/polls/date/options", AddOptionRequest(date = "2026-12-05")).body<PollDto>()
            assertEquals("2026-11-13T12:00:00Z", poll.deadline)
            assertEquals(
                listOf(
                    "STARTED" to "2026-11-06T12:00:00Z",
                    "DEADLINE_SOON" to "2026-11-11T12:00:00Z",
                    "LAST_DAY" to "2026-11-12T12:00:00Z",
                    "LAST_HOUR" to "2026-11-13T11:00:00Z",
                ),
                db.reminders(poll.id),
            )

            // A planner moves the deadline: the reminders follow.
            val moved = lea.putJson("/events/$eventId/polls/date/deadline", DeadlineRequest("2026-11-08T12:00:00Z")).body<PollDto>()
            assertEquals("2026-11-08T12:00:00Z", moved.deadline)
            assertEquals(
                listOf("STARTED" to "2026-11-06T12:00:00Z", "LAST_DAY" to "2026-11-07T12:00:00Z", "LAST_HOUR" to "2026-11-08T11:00:00Z"),
                db.reminders(poll.id),
            )
            assertEquals("DEADLINE_TOO_SOON", lea.code(lea.putJson("/events/$eventId/polls/date/deadline", DeadlineRequest("2026-11-06T12:30:00Z"))))

            // After the deadline, nobody votes until a planner extends it.
            clock.advance(Duration.ofDays(3))
            lea.signIn("google", "lea") // the access token expired with the jump
            val optionId = poll.options.single().id
            val closed = lea.putJson("/events/$eventId/polls/date/options/$optionId/vote", VoteRequest("PREFER"))
            assertEquals("POLL_CLOSED", lea.code(closed))
            lea.putJson("/events/$eventId/polls/date/deadline", DeadlineRequest("2026-11-12T12:00:00Z"))
            assertEquals(HttpStatusCode.OK, lea.putJson("/events/$eventId/polls/date/options/$optionId/vote", VoteRequest("PREFER")).status)
        }
    }

    @Test
    fun changingTheChosenDateResetsEveryRsvp() = apiWithDb(clock = TestClock()) { db ->
        val lea = user().also { it.signIn("google", "lea") }
        val max = user().also { it.signIn("google", "max") }
        val eventId = lea.createEvent()
        max.post("/events/$eventId/join")
        lea.postJson("/events/$eventId/polls/date/options", AddOptionRequest(date = "2026-12-05"))
        val poll = lea.postJson("/events/$eventId/polls/date/options", AddOptionRequest(date = "2026-12-06")).body<PollDto>()
        val (sat, sun) = poll.options.map { it.id }

        assertEquals(HttpStatusCode.NoContent, max.putJson("/events/$eventId/rsvp", RsvpRequest("COMING")).status)
        val validated = lea.putJson("/events/$eventId/polls/date/validated", ValidateRequest(sat)).body<PollDto>()
        assertEquals(sat, validated.validatedOptionId)
        assertFalse(validated.isOpen)
        assertTrue(db.reminders(poll.id).isEmpty(), "a decided poll sends no reminder")
        assertEquals("COMING", lea.rsvpOf(eventId, "Max"), "a first choice is not a change")

        max.putJson("/events/$eventId/rsvp", RsvpRequest("COMING"))
        lea.putJson("/events/$eventId/polls/date/validated", ValidateRequest(sun))
        assertEquals("NOT_ANSWERED", lea.rsvpOf(eventId, "Max"))

        // The old route still serves the first web client, with the new fields.
        val old = lea.get("/events/$eventId/poll").body<app.plano.contract.DatePollDto>()
        assertEquals(sun, old.validatedOptionId)
    }

    private suspend fun HttpClient.rsvpOf(eventId: String, name: String) =
        get("/events/$eventId").body<EventDto>().members.single { it.displayName == name }.rsvp

    @Test
    fun archivingLocksEditingAndOnlyTheOwnerDoesIt() = api(clock = TestClock()) {
        val lea = user().also { it.signIn("google", "lea") }
        val max = user().also { it.signIn("google", "max") }
        val eventId = lea.createEvent()
        max.post("/events/$eventId/join")

        assertEquals(HttpStatusCode.Forbidden, max.putJson("/events/$eventId/archived", ArchiveRequest(true)).status)
        assertEquals(HttpStatusCode.NoContent, lea.putJson("/events/$eventId/archived", ArchiveRequest(true)).status)
        assertTrue(lea.get("/events/$eventId").body<EventDto>().archived)
        assertEquals("EVENT_ARCHIVED", max.code(max.postJson("/events/$eventId/polls/date/options", AddOptionRequest(date = "2026-12-05"))))
        assertEquals("EVENT_ARCHIVED", max.code(max.putJson("/events/$eventId/rsvp", RsvpRequest("COMING"))))
        assertEquals(HttpStatusCode.OK, max.get("/events/$eventId/polls/date").status, "still readable")

        lea.putJson("/events/$eventId/archived", ArchiveRequest(false))
        assertEquals(HttpStatusCode.NoContent, max.putJson("/events/$eventId/rsvp", RsvpRequest("COMING")).status)
    }

    @Test
    fun strangersGet404OnEveryNewRoute() = api {
        val lea = user().also { it.signIn("google", "lea") }
        val eventId = lea.createEvent()
        val group = lea.createGroup("Escalade")
        val stranger = user().also { it.signIn("google", "stranger") }

        listOf(
            stranger.get("/events/$eventId/polls/place"),
            stranger.get("/events/$eventId/invitations"),
            stranger.postJson("/events/$eventId/invitations", InviteesRequest()),
            stranger.putJson("/events/$eventId/rsvp", RsvpRequest("COMING")),
            stranger.putJson("/events/$eventId/archived", ArchiveRequest(true)),
            stranger.putJson("/events/$eventId/polls/date/validated", ValidateRequest(null)),
            stranger.get("/groups/${group.id}"),
            stranger.get("/groups/${group.id}/events"),
            stranger.putJson("/groups/${group.id}/invite", UpdateGroupInviteRequest(10, 7)),
        ).forEach { assertEquals(HttpStatusCode.NotFound, it.status, it.call.request.url.toString()) }
    }

    private suspend fun Db.reminders(pollId: String): List<Pair<String, String>> = tx {
        query("select kind, due_at from poll_reminder where poll_id = ?::uuid and sent_at is null order by due_at", pollId) {
            it.getString(1) to it.instant(2).toString()
        }
    }
}
