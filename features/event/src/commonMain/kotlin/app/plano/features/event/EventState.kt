package app.plano.features.event

import kotlin.js.JsExport

@JsExport
data class MemberUi(
    val userId: String,
    val displayName: String,
    /** `Role` name. */
    val role: String,
    val isMe: Boolean,
)

@JsExport
data class DateOptionUi(
    val id: String,
    /** ISO date, `2026-12-05`. */
    val date: String,
    val prefer: Int,
    val cannot: Int,
    val ifMajority: Int,
    /** `VoteChoice` name, or `null` when I have not voted on this date. */
    val myChoice: String?,
    /** Most « Oui ». */
    val leadingOnPrefer: Boolean,
    /** Most « Oui » + « Si majorité ». */
    val leadingWithIfMajority: Boolean,
)

@JsExport
data class EventState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    /** `EventType` name. */
    val type: String = "",
    val description: String? = null,
    val canPlan: Boolean = false,
    val members: List<MemberUi> = emptyList(),
    /** In date order. */
    val options: List<DateOptionUi> = emptyList(),
    /** ISO date typed in the "add a date" field, or empty. */
    val newDate: String = "",
    /** Every `VoteChoice` name, in display order. */
    val choices: List<String> = emptyList(),
    val busy: Boolean = false,
    /** An `AppError.Kind` name or an `AddDateError` name. */
    val error: String? = null,
)
