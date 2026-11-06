package app.plano.contract

import kotlinx.serialization.Serializable

// Enum-like fields travel as their Kotlin enum names (`DINNER`, `PLANNER`, `PREFER`):
// the contract stays free of domain types, so domain modules never depend on each other through it.

@Serializable
data class SignInRequest(
    val idToken: String,
    /** Apple sends the name only on the first sign-in, outside the token. */
    val displayName: String? = null,
)

/** Native clients only: the web never sees tokens, they stay in httpOnly cookies. */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresInSeconds: Int,
)

@Serializable
data class UserDto(
    val id: String,
    val displayName: String,
    val email: String? = null,
)

@Serializable
data class EventSummaryDto(
    val id: String,
    val title: String,
    val type: String,
    val myRole: String,
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val type: String,
    val description: String? = null,
)

@Serializable
data class CreatedDto(
    val id: String,
)

@Serializable
data class MemberDto(
    val userId: String,
    val displayName: String,
    val role: String,
)

@Serializable
data class EventDto(
    val id: String,
    val title: String,
    val type: String,
    val description: String? = null,
    val myRole: String,
    val members: List<MemberDto>,
)

@Serializable
data class PollOptionDto(
    val id: String,
    /** ISO date, `2026-12-03`. */
    val date: String,
)

@Serializable
data class VoteDto(
    val optionId: String,
    val userId: String,
    val choice: String,
)

@Serializable
data class DatePollDto(
    val id: String,
    val options: List<PollOptionDto>,
    val votes: List<VoteDto>,
)

@Serializable
data class AddDateOptionRequest(
    val date: String,
)

@Serializable
data class VoteRequest(
    /** `null` removes the vote. */
    val choice: String? = null,
)

@Serializable
data class ErrorDto(
    val code: String,
    val message: String,
)
