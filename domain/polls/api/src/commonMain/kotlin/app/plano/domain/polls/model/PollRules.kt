package app.plano.domain.polls.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Every event has one poll of each kind (PRD, V1 scope: one place per event). */
enum class PollKind {
    DATE,

    /** Options are places (OpenStreetMap or from a maps link) or plain link cards. */
    PLACE,
    ;

    companion object {
        fun fromName(name: String): PollKind? = entries.firstOrNull { it.name == name }
    }
}

/** The reminder emails a poll sends (PRD, V1 scope), in order. */
enum class PollReminder(
    /** How long before the deadline it is due; `null` for [STARTED], due when the poll starts. */
    val beforeDeadline: Duration?,
) {
    STARTED(null),
    DEADLINE_SOON(2.days),
    LAST_DAY(1.days),
    LAST_HOUR(1.hours),
}

data class ScheduledReminder(
    val reminder: PollReminder,
    val dueAt: Instant,
)

object PollRules {
    /**
     * A poll starts with its first option. Its deadline is required (owner, 2026-11-25): prefilled to this, then
     * any planner can move it.
     */
    val DEFAULT_DEADLINE: Duration = 7.days

    /** The shortest deadline a planner can set, so the last-hour reminder still makes sense. */
    val MIN_DEADLINE_FROM_NOW: Duration = 1.hours

    /** Votes are taken until the deadline, and never once a planner validated an option. */
    fun isOpen(now: Instant, deadline: Instant?, validated: Boolean): Boolean =
        !validated && (deadline == null || now < deadline)

    fun canSetDeadline(now: Instant, deadline: Instant): Boolean = deadline >= now + MIN_DEADLINE_FROM_NOW

    /**
     * The reminders still to send for a poll that started at [startedAt] and ends at [deadline], seen at [now].
     * A reminder whose time is already past is skipped rather than sent late, except [PollReminder.STARTED]
     * when the poll starts right now. Reminders that would come before the start are skipped too: a two-day
     * poll gets no « two days left » email.
     */
    fun schedule(startedAt: Instant, deadline: Instant, now: Instant): List<ScheduledReminder> =
        PollReminder.entries.mapNotNull { reminder ->
            val dueAt = reminder.beforeDeadline?.let { deadline - it } ?: startedAt
            val stillUseful = when (reminder) {
                PollReminder.STARTED -> startedAt >= now
                else -> dueAt > startedAt && dueAt >= now
            }
            ScheduledReminder(reminder, dueAt).takeIf { stillUseful }
        }
}
