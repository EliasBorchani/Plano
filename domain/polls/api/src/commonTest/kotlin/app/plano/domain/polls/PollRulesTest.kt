package app.plano.domain.polls

import app.plano.domain.polls.model.PollReminder
import app.plano.domain.polls.model.PollRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PollRulesTest {
    private val start = Instant.parse("2026-12-01T10:00:00Z")

    @Test
    fun a_default_poll_gets_all_four_reminders() {
        val deadline = start + PollRules.DEFAULT_DEADLINE
        val schedule = PollRules.schedule(start, deadline, now = start)

        assertEquals(PollReminder.entries, schedule.map { it.reminder })
        assertEquals(
            listOf(start, deadline - 2.days, deadline - 1.days, deadline - 1.hours),
            schedule.map { it.dueAt },
        )
    }

    @Test
    fun a_short_poll_skips_the_reminders_that_would_come_before_it_starts() {
        val schedule = PollRules.schedule(start, start + 30.hours, now = start)

        assertEquals(listOf(PollReminder.STARTED, PollReminder.LAST_DAY, PollReminder.LAST_HOUR), schedule.map { it.reminder })
    }

    @Test
    fun moving_the_deadline_later_drops_reminders_already_past() {
        val now = start + 3.days
        val schedule = PollRules.schedule(start, start + 4.days, now)

        assertEquals(listOf(PollReminder.LAST_DAY, PollReminder.LAST_HOUR), schedule.map { it.reminder })
    }

    @Test
    fun votes_stop_at_the_deadline_or_on_validation() {
        val deadline = start + 1.days
        assertTrue(PollRules.isOpen(start, deadline, validated = false))
        assertFalse(PollRules.isOpen(deadline, deadline, validated = false))
        assertFalse(PollRules.isOpen(start, deadline, validated = true))
        assertTrue(PollRules.isOpen(start, null, validated = false), "a poll with no option yet has no deadline")
    }

    @Test
    fun a_deadline_must_leave_at_least_an_hour() {
        assertFalse(PollRules.canSetDeadline(start, start + 59.minutes))
        assertTrue(PollRules.canSetDeadline(start, start + 1.hours))
    }
}
