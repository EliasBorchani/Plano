package app.plano.server.links

import app.plano.contract.LinkCardDto
import app.plano.contract.LinkPreviewRequest
import app.plano.contract.Routes
import app.plano.domain.links.LinkCard
import app.plano.domain.links.LinkError
import app.plano.domain.links.LinkPlatform
import app.plano.domain.links.Links
import app.plano.server.auth.Auth
import app.plano.server.auth.requireUser
import app.plano.server.invalid
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jsoup.Jsoup

/** The server's part of the link pipeline: following short links and reading page titles (both need fetching). */
class LinkPreviewer(private val fetcher: SafeFetcher) {
    suspend fun preview(text: String): LinkCard {
        val card = Links.card(text) ?: invalid(LinkError.NOT_A_LINK.name)
        return when {
            card.needsResolution -> resolveShortLink(card)
            // Never Google pages: their content may not be stored (PRD, Risks).
            card.platform == LinkPlatform.OTHER && card.titleIsGuess -> enrich(card)
            else -> card
        }
    }

    /** Google short links only redirect: follow them on Google hosts, never load a page. */
    private suspend fun resolveShortLink(card: LinkCard): LinkCard {
        val target = fetcher.followRedirects(
            start = card.url,
            allowHost = ::isGoogleHost,
            isDone = { Links.card(it)?.let { it.platform == LinkPlatform.GOOGLE_MAPS && !it.needsResolution } == true },
        )
        return target?.let(Links::card) ?: card
    }

    private suspend fun enrich(card: LinkCard): LinkCard {
        val page = fetcher.fetchHtml(card.url) ?: return card
        val document = Jsoup.parse(page.html.inputStream(), page.charset, page.url)
        fun meta(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
            document.selectFirst("meta[property=$key], meta[name=$key]")?.attr("content")?.takeIf { it.isNotBlank() }
        }
        return card.withPage(
            pageTitle = meta("og:title", "twitter:title") ?: document.title().takeIf { it.isNotBlank() },
            pageDescription = meta("og:description", "twitter:description", "description"),
        )
    }

    private fun isGoogleHost(host: String) =
        host == "maps.app.goo.gl" || host == "goo.gl" || Regex("^([a-z]+\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$").matches(host)
}

fun Route.linkRoutes(auth: Auth, previewer: LinkPreviewer) {
    // Signed-in only (guests too): the server must not be an open fetcher for anyone on the internet.
    post(Routes.LINK_PREVIEW) {
        call.requireUser(auth)
        val card = previewer.preview(call.receive<LinkPreviewRequest>().text)
        call.respond(card.toDto())
    }
}

private fun LinkCard.toDto() = LinkCardDto(
    url = url,
    canonicalUrl = canonicalUrl,
    platform = platform.name,
    title = title,
    titleIsGuess = titleIsGuess,
    description = description,
    placeName = place?.name,
    latitude = place?.coordinates?.latitude,
    longitude = place?.coordinates?.longitude,
    coordinatesArePrecise = place?.coordinatesArePrecise == true,
    needsResolution = needsResolution,
)
