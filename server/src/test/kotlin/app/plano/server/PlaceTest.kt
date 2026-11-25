package app.plano.server

import app.plano.contract.ErrorDto
import app.plano.contract.FromLinkRequest
import app.plano.contract.PickPlaceRequest
import app.plano.contract.PlaceCandidateDto
import app.plano.contract.PlaceDto
import app.plano.server.places.Nominatim
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Duration
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PATHE_LINK =
    "https://www.google.com/maps/place/Path%C3%A9+%C3%8Ele+Seguin/@48.8221715,2.2349808,17z/data=!3m1!4b1!4m6!3m5!1s0x47e67b007c08e03b:0x7ed3505bdc74ce9a!8m2!3d48.8221715!4d2.2375611!16s%2Fg%2F11nr1sljhf?entry=tts"
private const val PADEL_LINK =
    "https://www.google.com/maps/place/4PADEL+Boulogne-Billancourt/@48.8281977,2.2398208,17z/data=!3m1!5s0x47e67afa9a627779:0x8883d7fcd2d277d1!4m6!3m5!1s0x47e67b13b6fdb1bf:0x96f4892f6a6a917a!8m2!3d48.8299075!4d2.24208!16s%2Fg%2F11xn7b9zrk?entry=tts"

// Shaped like the public Nominatim's jsonv2 answers for these queries on 2026-11-25.
private const val PATHE_JSON = """[{"place_id":1,"osm_type":"node","osm_id":14003653721,"lat":"48.82266","lon":"2.23794",
  "category":"amenity","type":"cinema","name":"Pathé","display_name":"Pathé, Parvis amont, Boulogne-Billancourt",
  "address":{"amenity":"Pathé","road":"Parvis amont","city":"Boulogne-Billancourt","postcode":"92100"}}]"""
private const val CROSSFIT_JSON = """[{"osm_type":"node","osm_id":6790321285,"lat":"48.82656","lon":"2.2403",
  "category":"leisure","name":"Crossfit le Trapèze","display_name":"Crossfit le Trapèze, 32, Rue Marcel Bontemps",
  "address":{"house_number":"32","road":"Rue Marcel Bontemps","city":"Boulogne-Billancourt","postcode":"92100"}}]"""

class PlaceTest {
    /** Requests the fake Nominatim received: path and decoded query. */
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange ->
            val query = URLDecoder.decode(exchange.requestURI.rawQuery.orEmpty(), "UTF-8")
            requests += "${exchange.requestURI.path}?$query"
            val q = Regex("(?:^|&)q=([^&]*)").find(query)?.groupValues?.get(1)
            val ids = Regex("osm_ids=([^&]*)").find(query)?.groupValues?.get(1)
            val body = when {
                exchange.requestURI.path == "/search" && q == "Pathé" -> PATHE_JSON
                exchange.requestURI.path == "/search" && q == "Crossfit" -> CROSSFIT_JSON
                exchange.requestURI.path == "/lookup" && ids == "N6790321285" -> CROSSFIT_JSON
                else -> "[]"
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
    private val nominatim = Nominatim("http://127.0.0.1:${server.address.port}/", minInterval = Duration.ofMillis(300))

    @AfterTest
    fun stop() = server.stop(0)

    private suspend fun HttpClient.search(q: String) = get("/places/search") { parameter("q", q) }

    private suspend fun HttpClient.fromLink(text: String) = post("/places/from-link") {
        contentType(ContentType.Application.Json)
        setBody(FromLinkRequest(text))
    }

    private suspend fun HttpClient.pick(osmRef: String) = post("/places") {
        contentType(ContentType.Application.Json)
        setBody(PickPlaceRequest(osmRef))
    }

    @Test
    fun searchThenPickStoresThePlaceOnce() = api(nominatim = nominatim) {
        val alice = user().apply { signIn("google", "alice") }
        val results = alice.search("Crossfit").body<List<PlaceCandidateDto>>()
        assertEquals(listOf("N6790321285"), results.map { it.osmRef })
        assertEquals("32 Rue Marcel Bontemps, 92100 Boulogne-Billancourt", results.single().address)

        // Same search again: from the cache. Picking a result just seen: no lookup.
        alice.search("Crossfit")
        val place = alice.pick("N6790321285").body<PlaceDto>()
        assertEquals(1, requests.size)
        assertEquals("OSM", place.source)
        assertEquals("Crossfit le Trapèze", place.name)
        assertEquals("https://www.google.com/maps/search/?api=1&query=48.82656,2.2403", place.googleMapsUrl)
        assertEquals(place, user().apply { signIn("google", "bob") }.pick("N6790321285").body<PlaceDto>())
    }

    @Test
    fun pickingAnUnseenPlaceLooksItUp() = api(nominatim = nominatim) {
        val alice = user().apply { signIn("google", "alice") }
        assertEquals("Crossfit le Trapèze", alice.pick("N6790321285").body<PlaceDto>().name)
        assertTrue(requests.single().startsWith("/lookup?osm_ids=N6790321285"))
        assertEquals(HttpStatusCode.NotFound, alice.pick("N1").status)
        assertEquals(HttpStatusCode.NotFound, alice.pick("DROP TABLE").status)
    }

    @Test
    fun aGoogleLinkMatchesTheOsmPlaceThroughItsFirstWord() = api(nominatim = nominatim) {
        val alice = user().apply { signIn("google", "alice") }
        val place = alice.fromLink(PATHE_LINK).body<PlaceDto>()
        assertEquals("OSM", place.source)
        assertEquals("N14003653721", place.osmRef)
        assertEquals("Pathé", place.name)
        // Full name first (nothing in OpenStreetMap), then « Pathé », both inside a box around the link's position.
        assertEquals(listOf("Pathé Île Seguin", "Pathé"), requests.map { Regex("q=([^&]*)").find(it)!!.groupValues[1] })
        assertTrue(requests.all { "bounded=1" in it && "viewbox=" in it })
    }

    @Test
    fun withNoMatchThePlaceIsMadeFromTheLink() = api(nominatim = nominatim) {
        val alice = user().apply { signIn("google", "alice") }
        val place = alice.fromLink(PADEL_LINK).body<PlaceDto>()
        assertEquals("LINK", place.source)
        assertEquals("4PADEL Boulogne-Billancourt", place.name)
        assertEquals(48.8299075, place.latitude)
        assertNull(place.osmRef)

        // The same place shared again (another map view): the same row, and no new search.
        val count = requests.size
        val again = alice.fromLink(PADEL_LINK.replace("@48.8281977,2.2398208,17z", "@48.83,2.24,15z")).body<PlaceDto>()
        assertEquals(place.id, again.id)
        assertEquals(count, requests.size)
    }

    @Test
    fun rulesAndErrors() = api(nominatim = nominatim) {
        assertEquals(HttpStatusCode.Unauthorized, user().search("Pathé").status)
        val alice = user().apply { signIn("google", "alice") }
        assertEquals("QUERY_TOO_SHORT", alice.search(" x ").body<ErrorDto>().code)
        assertEquals("NOT_A_PLACE_LINK", alice.fromLink("https://www.example.com/le-bistrot").body<ErrorDto>().code)
        assertEquals("NOT_A_LINK", alice.fromLink("chez Léa").body<ErrorDto>().code)
        assertEquals(0, requests.size)
    }

    @Test
    fun oneRequestAtATimeSpacedOut() = api(nominatim = nominatim) {
        val alice = user().apply { signIn("google", "alice") }
        val started = System.nanoTime()
        alice.search("un")
        alice.search("deux")
        alice.search("trois")
        assertTrue(System.nanoTime() - started >= Duration.ofMillis(600).toNanos())
    }

    @Test
    fun anUnreachableGeocoderIs503() = api(nominatim = Nominatim("http://127.0.0.1:9/")) {
        val alice = user().apply { signIn("google", "alice") }
        val response = alice.search("Pathé")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("SEARCH_UNAVAILABLE", response.body<ErrorDto>().code)
    }
}
