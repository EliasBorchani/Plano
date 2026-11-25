package app.plano.server

import app.plano.contract.DatePollDto
import app.plano.contract.ErrorDto
import app.plano.contract.EventDto
import app.plano.contract.EventSummaryDto
import app.plano.contract.GuestInviteDto
import app.plano.contract.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuestTest {
    private val inAWeek = LocalDate.now().plusDays(7).toString()
    private val inTwoWeeks = LocalDate.now().plusDays(14).toString()
    private val inThreeWeeks = LocalDate.now().plusDays(21).toString()

    @Test
    fun aGuestJoinsWithANameAndVotes() = api {
        val alice = user().apply { signIn("google", "alice") }
        val id = alice.createEvent("Dîner chez Léa")
        val optionId = alice.addDate(id, inAWeek).options.single().id

        val bob = user()
        assertEquals(GuestInviteDto(id, "Dîner chez Léa", "DINNER", emptyList()), bob.get("/auth/guest/$id").body())
        val guest = bob.joinAsGuest(id, "  Bob   Martin ").body<UserDto>()
        assertEquals("Bob Martin", guest.displayName)
        assertEquals(id, guest.guestEventId)
        assertEquals(guest, bob.get("/me").body<UserDto>())

        assertEquals("PLANNER", bob.get("/events/$id").body<EventDto>().myRole)
        assertEquals(HttpStatusCode.OK, bob.vote(id, optionId, "PREFER").status)
        assertEquals(listOf("Bob Martin"), user().get("/auth/guest/$id").body<GuestInviteDto>().guests.map { it.displayName })
    }

    @Test
    fun guestNamesFollowTheSharedRuleAndAreUniquePerEvent() = api {
        val alice = user().apply { signIn("google", "alice") }
        val id = alice.createEvent()

        assertEquals("NAME_MISSING", user().joinAsGuest(id, "   ").body<ErrorDto>().code)
        assertEquals("NAME_TOO_LONG", user().joinAsGuest(id, "x".repeat(41)).body<ErrorDto>().code)
        // Taken by the account « Alice », whatever the case and spacing.
        val taken = user().joinAsGuest(id, " alice ")
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("NAME_TAKEN", taken.body<ErrorDto>().code)

        assertEquals(HttpStatusCode.NotFound, user().joinAsGuest(UUID.randomUUID().toString(), "Bob").status)
        assertEquals(HttpStatusCode.NotFound, user().get("/auth/guest/${UUID.randomUUID()}").status)
    }

    @Test
    fun aGuestStaysInsideTheirEvent() = api {
        val alice = user().apply { signIn("google", "alice") }
        val first = alice.createEvent("Dîner")
        val second = alice.createEvent("Ski")
        val bob = user().apply { joinAsGuest(first, "Bob") }

        assertEquals(HttpStatusCode.Forbidden, bob.post("/events/$second/join").status)
        assertEquals(HttpStatusCode.NotFound, bob.get("/events/$second").status)
        assertEquals(listOf("Dîner"), bob.get("/events").body<List<EventSummaryDto>>().map { it.title })

        val create = bob.post("/events")
        assertEquals(HttpStatusCode.Forbidden, create.status)
        assertEquals("GUEST_CANNOT_CREATE", create.body<ErrorDto>().code)
    }

    @Test
    fun anyoneWithTheLinkCanContinueAsOneOfItsGuests() = api {
        val alice = user()
        val aliceAccount = alice.signIn("google", "alice")
        val id = alice.createEvent()
        val other = alice.createEvent("Autre")
        val optionId = alice.addDate(id, inAWeek).options.single().id

        val laptop = user()
        val bob = laptop.joinAsGuest(id, "Bob").body<UserDto>()
        laptop.vote(id, optionId, "CANNOT")

        val phone = user()
        assertEquals(bob, phone.post("/auth/guest/$id/as/${bob.id}").body<UserDto>())
        val votes = phone.get("/events/$id/poll").body<DatePollDto>().votes
        assertEquals(listOf("CANNOT"), votes.filter { it.userId == bob.id }.map { it.choice })
        // Both devices stay signed in.
        assertEquals(bob, laptop.get("/me").body<UserDto>())

        // Only a guest of that very event: never an account, never through another event's link.
        assertEquals(HttpStatusCode.NotFound, user().post("/auth/guest/$id/as/${aliceAccount.id}").status)
        assertEquals(HttpStatusCode.NotFound, user().post("/auth/guest/$other/as/${bob.id}").status)
    }

    @Test
    fun signingInWithANewIdentityTurnsTheGuestIntoAnAccount() = api {
        val alice = user().apply { signIn("google", "alice") }
        val id = alice.createEvent()
        val bob = user()
        val guest = bob.joinAsGuest(id, "Bobby").body<UserDto>()

        val account = bob.signIn("google", "bob")
        assertEquals(guest.id, account.id)
        assertEquals("Bobby", account.displayName)
        assertEquals("bob@example.com", account.email)
        assertNull(account.guestEventId)

        bob.createEvent("Ski")
        assertEquals(emptyList(), user().get("/auth/guest/$id").body<GuestInviteDto>().guests)
    }

    @Test
    fun signingInToAnExistingAccountMergesTheGuestAndKeepsTheAccountVotes() = api {
        val alice = user().apply { signIn("google", "alice") }
        val id = alice.createEvent()
        val first = alice.addDate(id, inAWeek).options.single().id
        val second = alice.addDate(id, inTwoWeeks).options.first { it.date == inTwoWeeks }.id

        val carolPhone = user()
        val carol = carolPhone.signIn("google", "carol")
        carolPhone.post("/events/$id/join")
        carolPhone.vote(id, first, "PREFER")

        val carolLaptop = user()
        val guest = carolLaptop.joinAsGuest(id, "Caro").body<UserDto>()
        carolLaptop.vote(id, first, "CANNOT")
        carolLaptop.vote(id, second, "IF_MAJORITY")
        carolLaptop.addDate(id, inThreeWeeks)

        assertEquals(carol.id, carolLaptop.signIn("google", "carol").id)

        val poll = alice.get("/events/$id/poll").body<DatePollDto>()
        assertEquals(
            setOf(first to "PREFER", second to "IF_MAJORITY"),
            poll.votes.filter { it.userId == carol.id }.map { it.optionId to it.choice }.toSet(),
        )
        assertEquals(emptyList(), poll.votes.filter { it.userId == guest.id })
        assertEquals(listOf("Alice", "Carol"), alice.get("/events/$id").body<EventDto>().members.map { it.displayName })
        assertEquals(HttpStatusCode.NotFound, user().post("/auth/guest/$id/as/${guest.id}").status)
    }
}
