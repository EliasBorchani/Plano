package app.plano.domain.places

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /** Haversine distance: plenty for « is this the same place », not for navigation. */
    fun distanceMeters(other: GeoPoint): Double {
        fun rad(deg: Double) = deg * PI / 180
        val dLat = rad(other.latitude - latitude)
        val dLng = rad(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(rad(latitude)) * cos(rad(other.latitude)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}

/** A geocoder result, before anyone picks it. */
data class PlaceCandidate(
    /** OpenStreetMap reference as Nominatim writes it: `N802009153` (node), `W…` (way), `R…` (relation). */
    val osmRef: String,
    val name: String,
    val address: String?,
    val point: GeoPoint,
    /** OpenStreetMap category (`amenity`, `leisure`, `place`…). */
    val category: String?,
)

enum class PlaceSource {
    /** Picked from OpenStreetMap (Nominatim). Shown with « © OpenStreetMap contributors ». */
    OSM,

    /** Made from a pasted link (name and position) when OpenStreetMap had no match. */
    LINK,
}

enum class PlaceError {
    QUERY_TOO_SHORT,
    QUERY_TOO_LONG,

    /** A link with no place in it (not a maps link, or no name and no position). */
    NOT_A_PLACE_LINK,

    /** A short maps link the server could not follow: paste the long link or search by name. */
    SHORT_LINK_UNRESOLVED,

    /** The geocoder is unreachable or too busy (one request per second for the whole server). */
    SEARCH_UNAVAILABLE,
}

object PlaceQuery {
    const val MIN = 2
    const val MAX = 200

    fun validate(query: String): PlaceError? {
        val trimmed = query.trim()
        return when {
            trimmed.length < MIN -> PlaceError.QUERY_TOO_SHORT
            trimmed.length > MAX -> PlaceError.QUERY_TOO_LONG
            else -> null
        }
    }
}

/** « Open in Google Maps », built from our own coordinates (PRD: nothing from Google is stored). */
fun googleMapsLinkOut(point: GeoPoint): String =
    "https://www.google.com/maps/search/?api=1&query=${point.latitude},${point.longitude}"
