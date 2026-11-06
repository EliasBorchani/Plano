package app.plano.domain.events.model

/** What an organizer fills in. [validate] runs in every client and again on the server. */
data class NewEvent(
    val title: String,
    val type: EventType,
    val description: String?,
) {
    fun validate(): List<NewEventError> = buildList {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) add(NewEventError.TITLE_MISSING)
        if (trimmed.length > TITLE_MAX) add(NewEventError.TITLE_TOO_LONG)
        if ((description?.length ?: 0) > DESCRIPTION_MAX) add(NewEventError.DESCRIPTION_TOO_LONG)
    }

    fun normalized(): NewEvent = copy(
        title = title.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
    )

    companion object {
        const val TITLE_MAX = 80
        const val DESCRIPTION_MAX = 2000
    }
}

enum class NewEventError {
    TITLE_MISSING,
    TITLE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
}
