package app.plano.domain.links

enum class LinkPlatform {
    GOOGLE_MAPS,

    /** No parser for this host: the generic card (title from the page, else from the address). */
    OTHER,
}

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

/** What a maps link says about a place. Matched to the geocoder later (PRD step 4). */
data class PlaceHint(
    val name: String?,
    val coordinates: Coordinates?,
    /**
     * True for the place's own position (`!3d…!4d…`, a `q=` pin). False for the map view's center (`@lat,lng`),
     * which can be a kilometre away: a hint only.
     */
    val coordinatesArePrecise: Boolean,
)

/** The editable card a pasted link becomes. Nothing here is Google content beyond what the user pasted. */
data class LinkCard(
    /** The cleaned link (tracking stripped): the link-out. */
    val url: String,
    /** Two links with the same canonical address are one option. */
    val canonicalUrl: String,
    val platform: LinkPlatform,
    /** Null when nothing gives one (a maps link with coordinates only): the UI asks for it. */
    val title: String?,
    /** True when [title] is only guessed from the address words: a page title may replace it. */
    val titleIsGuess: Boolean,
    val description: String?,
    val place: PlaceHint?,
    /** A short link the server must follow before parsing (a browser cannot read cross-site redirects). */
    val needsResolution: Boolean,
) {
    /** Adds what the page itself says (its title and description), keeping what a parser found. */
    fun withPage(pageTitle: String?, pageDescription: String?): LinkCard {
        val title = pageTitle?.let(::tidy)?.take(TITLE_MAX)?.takeIf { it.isNotEmpty() }
        val description = pageDescription?.let(::tidy)?.take(DESCRIPTION_MAX)?.takeIf { it.isNotEmpty() }
        return copy(
            title = if (titleIsGuess && title != null) title else this.title,
            titleIsGuess = titleIsGuess && title == null,
            description = this.description ?: description,
        )
    }

    companion object {
        const val TITLE_MAX = 200
        const val DESCRIPTION_MAX = 500

        private fun tidy(text: String) = text.replace(Regex("\\s+"), " ").trim()
    }
}

enum class LinkError {
    NOT_A_LINK,
}
