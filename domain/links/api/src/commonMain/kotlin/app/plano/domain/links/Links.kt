package app.plano.domain.links

/**
 * The link pipeline's pure steps (PRD, Link handling): clean, identify, parse. Fetching (short links, page titles)
 * happens on the server, which then calls [card] again on what it found.
 */
object Links {
    private val URL_IN_TEXT = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "igshid", "igsh", "mc_cid", "mc_eid",
        "_hsenc", "_hsmi", "si",
        // Google Maps share parameters
        "entry", "g_ep", "skid", "g_st", "shorturl",
    )
    private val TRACKING_PREFIXES = listOf("utm_", "pk_")

    /** The card for whatever the user pasted (a link alone, or share text around one). Null when there is no link. */
    fun card(text: String): LinkCard? {
        val url = clean(text) ?: return null
        return GoogleMaps.card(url) ?: genericCard(url)
    }

    /** The link in [text], without tracking parameters or fragment, with its query sorted (canonical order). */
    fun clean(text: String): WebUrl? {
        val raw = URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ';', '!', '?', '»', '"', '\'') ?: text.trim()
        val url = WebUrl.parse(raw) ?: return null
        val unwrapped = unwrapConsent(url) ?: url
        return unwrapped.copy(
            query = unwrapped.query
                .filterNot { (key, _) -> isTracking(WebUrl.decode(key).lowercase()) }
                .sortedBy { it.first },
            path = unwrapped.path.trimEnd('/'),
        )
    }

    /** In the EU, a Google redirect can land on a consent page whose `continue` parameter is the real address. */
    private fun unwrapConsent(url: WebUrl): WebUrl? {
        if (!url.host.startsWith("consent.google.")) return null
        return url.param("continue")?.let(WebUrl::parse)
    }

    private fun isTracking(key: String) = key in TRACKING_PARAMS || TRACKING_PREFIXES.any { key.startsWith(it) }

    private fun genericCard(url: WebUrl): LinkCard {
        val text = url.toString()
        return LinkCard(
            url = text,
            canonicalUrl = text,
            platform = LinkPlatform.OTHER,
            title = titleFromAddress(url),
            titleIsGuess = true,
            description = null,
            place = null,
            needsResolution = false,
        )
    }

    /** `…/restaurants/le-petit-bistrot-48213.html` → « Le petit bistrot »; no usable word → the host. */
    internal fun titleFromAddress(url: WebUrl): String {
        val words = url.segments.asReversed().asSequence()
            .map { WebUrl.decode(it, plusAsSpace = true).substringBeforeLast('.').takeIf { s -> s.isNotEmpty() } ?: it }
            .filterNot { looksLikeId(it) }
            .map { segment -> segment.split('-', '_', ' ').filter { it.isNotEmpty() } }
            .map { parts -> parts.dropLastWhile { it.all(Char::isDigit) }.ifEmpty { parts } }
            .firstOrNull { parts -> parts.any { part -> part.any(Char::isLetter) } }
        val title = words?.joinToString(" ")?.replaceFirstChar { it.uppercase() }
        return (title ?: url.host.removePrefix("www.")).take(LinkCard.TITLE_MAX)
    }

    private fun looksLikeId(segment: String): Boolean =
        segment.all(Char::isDigit) || (segment.length >= 16 && '-' !in segment && segment.any(Char::isDigit))
}
