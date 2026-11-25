package app.plano.server

import app.plano.contract.ErrorDto
import app.plano.contract.LinkCardDto
import app.plano.contract.LinkPreviewRequest
import app.plano.server.links.AddressPolicy
import app.plano.server.links.SafeFetcher
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PATHE_LONG =
    "https://www.google.com/maps/place/Path%C3%A9+%C3%8Ele+Seguin/@48.8221715,2.2349808,17z/data=!3m1!4b1!4m6!3m5!1s0x47e67b007c08e03b:0x7ed3505bdc74ce9a!8m2!3d48.8221715!4d2.2375611!16s%2Fg%2F11nr1sljhf?entry=tts"

/**
 * A fake internet on 127.0.0.1: every host name resolves to it, so `maps.app.goo.gl:<port>` reaches this server.
 * The real policy forbids loopback, so these tests relax it; [defaultPolicyRefusesPrivateAddresses] does not.
 */
class LinkPreviewTest {
    private val hits = AtomicInteger()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange ->
            hits.incrementAndGet()
            routes[exchange.requestURI.path]?.invoke(exchange) ?: exchange.reply(404, "text/plain", "")
        }
        start()
    }
    private val port = server.address.port
    private val routes = mutableMapOf<String, (HttpExchange) -> Unit>()

    private val toLoopback = Dns { listOf(InetAddress.getLoopbackAddress()) }
    private val relaxed = SafeFetcher(AddressPolicy(allowPrivate = true, allowAnyPort = true), dns = toLoopback)

    @AfterTest
    fun stop() = server.stop(0)

    private fun redirect(path: String, location: String) {
        routes[path] = { it.responseHeaders.add("Location", location); it.reply(302, "text/plain", "") }
    }

    private fun page(path: String, html: String, type: String = "text/html; charset=utf-8") {
        routes[path] = { it.reply(200, type, html) }
    }

    private suspend fun HttpClient.preview(text: String) = post("/links/preview") {
        contentType(ContentType.Application.Json)
        setBody(LinkPreviewRequest(text))
    }

    @Test
    fun aShortLinkIsFollowedToTheLongOne() = api(linkFetcher = relaxed) {
        redirect("/AbCd", PATHE_LONG)
        val alice = user().apply { signIn("google", "alice") }
        val card = alice.preview("Pathé\nhttp://maps.app.goo.gl:$port/AbCd").body<LinkCardDto>()
        assertFalse(card.needsResolution)
        assertEquals("Pathé Île Seguin", card.title)
        assertEquals(48.8221715, card.latitude)
        assertTrue(card.coordinatesArePrecise)
        // The long Google address itself was never requested.
        assertEquals(1, hits.get())
    }

    @Test
    fun theEuConsentPageCarriesTheDestination() = api(linkFetcher = relaxed) {
        redirect("/Consent", "https://consent.google.com/ml?continue=${URLEncoder.encode(PATHE_LONG, "UTF-8")}&gl=FR")
        val alice = user().apply { signIn("google", "alice") }
        assertEquals("Pathé Île Seguin", alice.preview("http://maps.app.goo.gl:$port/Consent").body<LinkCardDto>().title)
    }

    @Test
    fun aShortLinkLeavingGoogleOrLoopingStaysUnresolved() = api(linkFetcher = relaxed) {
        redirect("/Away", "http://evil.example:$port/x")
        redirect("/Loop", "http://maps.app.goo.gl:$port/Loop")
        val alice = user().apply { signIn("google", "alice") }

        assertTrue(alice.preview("http://maps.app.goo.gl:$port/Away").body<LinkCardDto>().needsResolution)
        assertEquals(1, hits.getAndSet(0))
        assertTrue(alice.preview("http://maps.app.goo.gl:$port/Loop").body<LinkCardDto>().needsResolution)
        assertEquals(6, hits.get())
    }

    @Test
    fun anUnknownPageGivesItsTitle() = api(linkFetcher = relaxed) {
        page(
            "/menu",
            """<html><head><title>Accueil</title><meta property="og:title" content="Le Petit Bistrot &amp; Co">
              <meta name="description" content="Cuisine de saison"></head><body>…</body></html>""",
        )
        redirect("/old-menu", "/menu")
        page("/plain", "<title>x</title>", type = "text/plain")
        val alice = user().apply { signIn("google", "alice") }

        val card = alice.preview("http://bistrot.example:$port/old-menu?utm_source=wa").body<LinkCardDto>()
        assertEquals("Le Petit Bistrot & Co", card.title)
        assertFalse(card.titleIsGuess)
        assertEquals("Cuisine de saison", card.description)
        assertEquals("http://bistrot.example:$port/old-menu", card.url)

        val plain = alice.preview("http://bistrot.example:$port/plain").body<LinkCardDto>()
        assertEquals("Plain", plain.title)
        assertTrue(plain.titleIsGuess)
    }

    @Test
    fun aHugePageIsReadOnlyUpToTheLimit() = runBlocking {
        page("/big", "<title>Grand</title>" + "x".repeat(2_000_000))
        val small = SafeFetcher(AddressPolicy(allowPrivate = true, allowAnyPort = true), dns = toLoopback, maxBytes = 1024)
        assertEquals(1024, small.fetchHtml("http://big.example:$port/big")?.html?.size)
    }

    @Test
    fun defaultPolicyRefusesPrivateAddresses() = runBlocking {
        page("/secret", "<title>Internal</title>")
        // The default policy, with host names that resolve to loopback (like a DNS answer pointing inside).
        val strict = SafeFetcher(AddressPolicy(allowAnyPort = true), dns = toLoopback)
        assertNull(strict.fetchHtml("http://innocent.example:$port/secret"))
        assertNull(strict.fetchHtml("http://127.0.0.1:$port/secret"))
        assertNull(strict.fetchHtml("http://[::1]:$port/secret"))
        assertEquals(0, hits.get())

        // And ports other than 80 and 443.
        assertNull(SafeFetcher(AddressPolicy(allowPrivate = true), dns = toLoopback).fetchHtml("http://a.example:$port/secret"))
        assertEquals(0, hits.get())

        for (address in listOf("10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "fd00::1", "::ffff:127.0.0.1")) {
            assertFalse(AddressPolicy.isPublic(InetAddress.getByName(address)), address)
        }
        assertTrue(AddressPolicy.isPublic(InetAddress.getByName("142.250.179.110")))
    }

    @Test
    fun previewNeedsASessionAndALink() = api(linkFetcher = relaxed) {
        assertEquals(HttpStatusCode.Unauthorized, user().preview("https://example.com").status)
        val alice = user().apply { signIn("google", "alice") }
        val notALink = alice.preview("chez Léa")
        assertEquals(HttpStatusCode.BadRequest, notALink.status)
        assertEquals("NOT_A_LINK", notALink.body<ErrorDto>().code)
    }
}

private fun HttpExchange.reply(status: Int, type: String, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.add("Content-Type", type)
    sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
    if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) } else close()
}
