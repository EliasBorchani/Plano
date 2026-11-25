package app.plano.server.places

import app.plano.domain.places.GeoPoint
import app.plano.domain.places.PlaceCandidate
import app.plano.domain.places.PlaceError
import app.plano.domain.places.PlaceMatching
import app.plano.server.ApiError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.cos

private val log = LoggerFactory.getLogger(Nominatim::class.java)

/**
 * The public Nominatim server, used as its policy asks (PRD decision for V1): search on submit only, at most one
 * request per second for the whole server, an identifying User-Agent, and results cached.
 */
class Nominatim(
    baseUrl: String = "https://nominatim.openstreetmap.org/",
    private val minInterval: Duration = Duration.ofSeconds(1),
    private val clock: Clock = Clock.systemUTC(),
    /** How long a caller may queue behind others before getting `SEARCH_UNAVAILABLE`. */
    private val maxWait: Duration = Duration.ofSeconds(8),
    private val cacheTtl: Duration = Duration.ofHours(24),
    private val cacheSize: Int = 1000,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val client = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Mutex()
    private var lastCall = Instant.EPOCH
    private val cache = object : LinkedHashMap<String, Pair<Instant, List<PlaceCandidate>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Instant, List<PlaceCandidate>>>) = size > cacheSize
    }

    /** Results for [query], inside a box around [near] when given (link matching). */
    suspend fun search(query: String, near: GeoPoint? = null): List<PlaceCandidate> {
        val url = base.newBuilder().addPathSegment("search")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("limit", "8")
            .apply {
                if (near != null) {
                    val dLat = PlaceMatching.SEARCH_RADIUS_DEGREES
                    val dLng = dLat / cos(Math.toRadians(near.latitude))
                    addQueryParameter("viewbox", "${near.longitude - dLng},${near.latitude - dLat},${near.longitude + dLng},${near.latitude + dLat}")
                    addQueryParameter("bounded", "1")
                }
            }
        return cached(url)
    }

    /** One place by its reference (`N802009153`), or null when OpenStreetMap no longer has it. */
    suspend fun lookup(osmRef: String): PlaceCandidate? =
        cached(base.newBuilder().addPathSegment("lookup").addQueryParameter("osm_ids", osmRef)).firstOrNull()

    /** Results of a recent search, when one of them has this reference: picking it costs no request. */
    fun recent(osmRef: String): PlaceCandidate? = synchronized(cache) {
        cache.values.asSequence().filter { fresh(it.first) }.flatMap { it.second }.firstOrNull { it.osmRef == osmRef }
    }

    private suspend fun cached(builder: HttpUrl.Builder): List<PlaceCandidate> {
        val url = builder
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("accept-language", "fr")
            .build()
        val key = url.toString().lowercase()
        synchronized(cache) { cache[key]?.takeIf { fresh(it.first) }?.let { return it.second } }
        val results = call(url)
        synchronized(cache) { cache[key] = clock.instant() to results }
        return results
    }

    private fun fresh(at: Instant) = Duration.between(at, clock.instant()) < cacheTtl

    private suspend fun call(url: HttpUrl): List<PlaceCandidate> {
        try {
            return withTimeout(maxWait.toMillis()) {
                gate.withLock {
                    val wait = Duration.between(clock.instant(), lastCall.plus(minInterval))
                    if (!wait.isNegative) delay(wait.toMillis())
                    try {
                        fetch(url)
                    } finally {
                        lastCall = clock.instant()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            unavailable("busy")
        }
    }

    private suspend fun fetch(url: HttpUrl): List<PlaceCandidate> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) unavailable("HTTP ${response.code}")
                json.decodeFromString<List<NominatimPlace>>(response.body.string()).mapNotNull { it.toCandidate() }
            }
        } catch (e: IOException) {
            unavailable(e.message)
        }
    }

    private fun unavailable(reason: String?): Nothing {
        log.warn("Nominatim unavailable: {}", reason)
        throw ApiError(HttpStatusCode.ServiceUnavailable, PlaceError.SEARCH_UNAVAILABLE.name)
    }

    companion object {
        // Nominatim's policy asks for an application name and a way to learn more (owner agreed, 2026-11-25).
        const val USER_AGENT = "Plano/0.1 (+https://github.com/EliasBorchani/Plano)"
    }
}

@Serializable
private data class NominatimPlace(
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    val lat: String,
    val lon: String,
    val name: String? = null,
    @SerialName("display_name") val displayName: String = "",
    val category: String? = null,
    val address: Map<String, String> = emptyMap(),
) {
    fun toCandidate(): PlaceCandidate? {
        val letter = when (osmType) {
            "node" -> "N"
            "way" -> "W"
            "relation" -> "R"
            else -> return null
        }
        val point = GeoPoint(lat.toDoubleOrNull() ?: return null, lon.toDoubleOrNull() ?: return null)
        val shownName = name?.takeIf { it.isNotBlank() } ?: displayName.substringBefore(',').trim()
        return PlaceCandidate("$letter${osmId ?: return null}", shownName, shortAddress(), point, category)
    }

    /** « 32 Rue Marcel Bontemps, 92100 Boulogne-Billancourt ». */
    private fun shortAddress(): String? {
        val street = listOfNotNull(address["house_number"], address["road"] ?: address["pedestrian"] ?: address["square"])
            .joinToString(" ")
        val city = address["city"] ?: address["town"] ?: address["village"] ?: address["municipality"]
        val town = listOfNotNull(address["postcode"], city).joinToString(" ")
        return listOf(street, town).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
    }
}
