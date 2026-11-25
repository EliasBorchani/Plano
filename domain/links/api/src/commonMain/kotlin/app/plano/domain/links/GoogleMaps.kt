package app.plano.domain.links

/**
 * Google Maps links, read from the address text only (PRD: nothing else from Google is fetched or stored).
 * Long form: `/maps/place/<name>/@<view lat>,<view lng>,<zoom>z/data=…!3d<lat>!4d<lng>…`.
 */
internal object GoogleMaps {
    private val GOOGLE_HOST = Regex("^(www\\.|maps\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$")
    private val PLACE_COORDINATES = Regex("!3d(-?\\d{1,3}(?:\\.\\d+)?)!4d(-?\\d{1,3}(?:\\.\\d+)?)")
    private val LAT_LNG = Regex("^\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)")

    fun isShortLink(url: WebUrl): Boolean =
        url.host == "maps.app.goo.gl" || (url.host == "goo.gl" && url.segments.firstOrNull() == "maps")

    fun card(url: WebUrl): LinkCard? {
        if (isShortLink(url)) return shortLinkCard(url)
        if (!isLongLink(url)) return null

        val segments = url.segments.drop(if (url.host.startsWith("maps.")) 0 else 1)
        val named = segments.zipWithNext().firstOrNull { (kind, _) -> kind == "place" || kind == "search" }?.second
            ?.takeUnless { it.startsWith("@") || it.startsWith("data=") }
            ?.let { WebUrl.decode(it, plusAsSpace = true).trim() }
        val queried = url.param("q") ?: url.param("query")

        // Best first: the place's own position, then a pin given as text, then the map view's center.
        val precise = PLACE_COORDINATES.findAll(url.path).lastOrNull()?.toPair()
            ?: named?.let(::latLng)
            ?: queried?.let(::latLng)
        val viewport = segments.firstOrNull { it.startsWith("@") }?.let { latLng(it.removePrefix("@")) }
        val name = (named ?: queried)?.takeIf { latLng(it) == null && it.isNotEmpty() }
        val coordinates = precise ?: viewport

        val canonical = when {
            name != null && precise != null -> "https://www.google.com/maps/place/${WebUrl.encode(name)}/@${precise.first},${precise.second},17z"
            precise != null -> "https://www.google.com/maps/search/?api=1&query=${precise.first},${precise.second}"
            name != null && coordinates == null -> "https://www.google.com/maps/search/?api=1&query=${WebUrl.encode(name)}"
            else -> url.toString()
        }
        return LinkCard(
            url = url.toString(),
            canonicalUrl = canonical,
            platform = LinkPlatform.GOOGLE_MAPS,
            title = name?.take(LinkCard.TITLE_MAX),
            titleIsGuess = false,
            description = null,
            place = PlaceHint(
                name = name,
                coordinates = coordinates?.let { Coordinates(it.first.toDouble(), it.second.toDouble()) },
                coordinatesArePrecise = precise != null,
            ),
            needsResolution = false,
        )
    }

    private fun isLongLink(url: WebUrl): Boolean {
        if (!GOOGLE_HOST.matches(url.host)) return false
        return url.host.startsWith("maps.") || url.segments.firstOrNull() == "maps"
    }

    private fun shortLinkCard(url: WebUrl): LinkCard = LinkCard(
        url = url.toString(),
        canonicalUrl = url.toString(),
        platform = LinkPlatform.GOOGLE_MAPS,
        title = null,
        titleIsGuess = false,
        description = null,
        place = null,
        needsResolution = true,
    )

    /** Coordinates kept as written, so the canonical address is identical on the JVM and in JavaScript. */
    private fun latLng(text: String): Pair<String, String>? {
        val match = LAT_LNG.find(text) ?: return null
        val (lat, lng) = match.destructured
        if (lat.toDouble() !in -90.0..90.0 || lng.toDouble() !in -180.0..180.0) return null
        return lat to lng
    }

    private fun MatchResult.toPair(): Pair<String, String>? {
        val (lat, lng) = destructured
        return latLng("$lat,$lng")
    }
}
