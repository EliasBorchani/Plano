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
    /** Set for a guest: the one event this guest belongs to. */
    val guestEventId: String? = null,
    /** Accounts only: others can find this person by name. */
    val searchable: Boolean = false,
)

@Serializable
data class UpdateProfileRequest(
    val searchable: Boolean,
)

/** Someone a planner can invite by name: a searchable account. */
@Serializable
data class PersonDto(
    val id: String,
    val displayName: String,
)

@Serializable
data class GuestDto(
    val id: String,
    val displayName: String,
)

/** Public: what an event link shows before anyone signs in. */
@Serializable
data class GuestInviteDto(
    val eventId: String,
    val title: String,
    val type: String,
    val guests: List<GuestDto>,
)

@Serializable
data class JoinAsGuestRequest(
    val displayName: String,
)

@Serializable
data class EventSummaryDto(
    val id: String,
    val title: String,
    val type: String,
    val myRole: String,
    /** `MemberStatus` name: a `LEFT` event shows only its title and type. */
    val myStatus: String = "JOINED",
    val archived: Boolean = false,
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val type: String,
    val description: String? = null,
    val invitees: InviteesRequest = InviteesRequest(),
)

/** One invite step: individuals, groups after the untick modal, and emails. A person picked twice is invited once. */
@Serializable
data class InviteesRequest(
    val userIds: List<String> = emptyList(),
    val groups: List<GroupPickDto> = emptyList(),
    val emails: List<EmailInviteDto> = emptyList(),
)

@Serializable
data class GroupPickDto(
    val groupId: String,
    /** The members the organizer unticked in the modal. */
    val untickedUserIds: List<String> = emptyList(),
)

@Serializable
data class EmailInviteDto(
    val email: String,
    /** How the others see them until they choose a name; defaults to the part before the `@`. */
    val displayName: String? = null,
)

/** Personal links for the people invited by email who have no account (emails are not sent yet). */
@Serializable
data class InvitationLinkDto(
    val email: String,
    val userId: String,
    val token: String,
)

@Serializable
data class InvitationsDto(
    val links: List<InvitationLinkDto>,
)

/** Public: what a personal invitation link shows before signing in with it. */
@Serializable
data class InvitationPreviewDto(
    val eventId: String,
    val title: String,
    val type: String,
    val displayName: String,
)

@Serializable
data class RsvpRequest(
    /** `Rsvp` name. */
    val answer: String,
)

@Serializable
data class ArchiveRequest(
    val archived: Boolean,
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
    /** `MemberStatus` name. */
    val status: String = "JOINED",
    /** `Rsvp` name. */
    val rsvp: String = "NOT_ANSWERED",
    val isGuest: Boolean = false,
)

@Serializable
data class EventDto(
    val id: String,
    val title: String,
    val type: String,
    val description: String? = null,
    val myRole: String,
    val members: List<MemberDto>,
    val myStatus: String = "JOINED",
    val archived: Boolean = false,
    /** The groups invitees were picked from: context only. */
    val groups: List<GroupSummaryDto> = emptyList(),
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
    /** ISO instant; null until the first option starts the poll. */
    val deadline: String? = null,
    val validatedOptionId: String? = null,
    val isOpen: Boolean = true,
)

/** A poll of any kind (`PollKind` name). Each option has exactly one of date, place or link. */
@Serializable
data class PollDto(
    val id: String,
    val kind: String,
    val options: List<OptionDto>,
    val votes: List<VoteDto>,
    val deadline: String? = null,
    val validatedOptionId: String? = null,
    val isOpen: Boolean,
)

@Serializable
data class OptionDto(
    val id: String,
    val date: String? = null,
    val place: PlaceDto? = null,
    val link: LinkOptionDto? = null,
)

@Serializable
data class LinkOptionDto(
    val url: String,
    val title: String,
)

/** Exactly one field: `date` for the date poll, `placeId` or `link` for the place poll. */
@Serializable
data class AddOptionRequest(
    val date: String? = null,
    val placeId: String? = null,
    val link: LinkOptionDto? = null,
)

@Serializable
data class DeadlineRequest(
    /** ISO instant. */
    val deadline: String,
)

@Serializable
data class ValidateRequest(
    /** `null` cancels the choice. */
    val optionId: String? = null,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
)

@Serializable
data class GroupSummaryDto(
    val id: String,
    val name: String,
    val memberCount: Int = 0,
)

@Serializable
data class GroupInviteDto(
    val token: String,
    val maxUses: Int,
    val uses: Int,
    /** ISO instant. */
    val expiresAt: String,
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val members: List<PersonDto>,
    val invite: GroupInviteDto,
)

/** Change the current link's limits; `newLink` replaces the token, which kills the old link. */
@Serializable
data class UpdateGroupInviteRequest(
    val maxUses: Int,
    val expiresInDays: Int,
    val newLink: Boolean = false,
)

/** Public: what a group invite link shows. */
@Serializable
data class GroupInvitePreviewDto(
    val groupId: String,
    val name: String,
    val memberCount: Int,
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

@Serializable
data class LinkPreviewRequest(
    /** What the user pasted: a link, or share text with a link in it. */
    val text: String,
)

@Serializable
data class LinkCardDto(
    val url: String,
    val canonicalUrl: String,
    /** `LinkPlatform` name. */
    val platform: String,
    val title: String? = null,
    val titleIsGuess: Boolean,
    val description: String? = null,
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinatesArePrecise: Boolean = false,
    /** Still a short link: following it failed, the user should paste the long link or search by name. */
    val needsResolution: Boolean,
)

/** A geocoder result: not stored until someone picks it. */
@Serializable
data class PlaceCandidateDto(
    val osmRef: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
)

@Serializable
data class PickPlaceRequest(
    val osmRef: String,
)

@Serializable
data class FromLinkRequest(
    /** A pasted maps link, or share text with one in it. */
    val text: String,
)

@Serializable
data class PlaceDto(
    val id: String,
    /** `PlaceSource` name: `OSM` places are shown with « © OpenStreetMap contributors ». */
    val source: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val osmRef: String? = null,
    val linkUrl: String? = null,
    val googleMapsUrl: String,
)
