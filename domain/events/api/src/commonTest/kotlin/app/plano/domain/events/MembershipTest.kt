package app.plano.domain.events

import app.plano.domain.events.model.EmailAddress
import app.plano.domain.events.model.GroupPick
import app.plano.domain.events.model.Invitees
import app.plano.domain.events.model.Rsvp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipTest {
    @Test
    fun a_person_picked_twice_is_invited_once_and_unticked_members_are_left_out() {
        val climbing = GroupPick("g1", members = listOf("lea", "max", "sara", "tom"), unticked = setOf("tom"))
        val school = GroupPick("g2", members = listOf("max", "nina", "lea"), unticked = emptySet())

        val invitees = Invitees.resolve(
            individuals = listOf("sara", "omar"),
            groups = listOf(climbing, school),
            inviter = "lea",
            alreadyMembers = setOf("nina"),
        )

        assertEquals(listOf("sara", "omar", "max"), invitees)
    }

    @Test
    fun only_a_change_of_choice_resets_the_rsvp() {
        assertFalse(Rsvp.resetsOnChange(null, "sat"), "a first choice is not a change")
        assertFalse(Rsvp.resetsOnChange("sat", "sat"))
        assertTrue(Rsvp.resetsOnChange("sat", "sun"))
        assertTrue(Rsvp.resetsOnChange("sat", null), "cancelling the choice reopens the question")
    }

    @Test
    fun emails_are_normalized_and_sanity_checked() {
        assertEquals("sara@example.fr", EmailAddress.normalize("  Sara@Example.FR "))
        assertTrue(EmailAddress.isValid("sara.martin@example.fr"))
        assertFalse(EmailAddress.isValid("sara"))
        assertFalse(EmailAddress.isValid("sara@localhost"))
        assertFalse(EmailAddress.isValid("sa ra@example.fr"))
        assertFalse(EmailAddress.isValid("a@b@example.fr"))
        assertEquals("sara.martin", EmailAddress.defaultName("Sara.Martin@example.fr"))
    }
}
