package app.plano.server.places

import app.plano.contract.FromLinkRequest
import app.plano.contract.PickPlaceRequest
import app.plano.contract.PlaceCandidateDto
import app.plano.contract.PlaceDto
import app.plano.contract.Routes
import app.plano.domain.links.LinkPlatform
import app.plano.domain.places.GeoPoint
import app.plano.domain.places.PlaceCandidate
import app.plano.domain.places.PlaceError
import app.plano.domain.places.PlaceMatching
import app.plano.domain.places.PlaceQuery
import app.plano.domain.places.PlaceSource
import app.plano.domain.places.googleMapsLinkOut
import app.plano.server.ApiError
import app.plano.server.Db
import app.plano.server.auth.Auth
import app.plano.server.auth.requireUser
import app.plano.server.invalid
import app.plano.server.links.LinkPreviewer
import app.plano.server.notFound
import app.plano.server.query
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.sql.ResultSet
import java.util.UUID

private val OSM_REF = Regex("^[NWR][0-9]{1,15}$")

class Places(private val db: Db, private val nominatim: Nominatim, private val links: LinkPreviewer) {
    suspend fun search(query: String): List<PlaceCandidate> {
        PlaceQuery.validate(query)?.let { invalid(it.name) }
        return nominatim.search(query)
    }

    /** Stores the OpenStreetMap place [osmRef] once (data from our own recent search, else one lookup). */
    suspend fun pick(osmRef: String, userId: UUID): PlaceDto {
        if (!OSM_REF.matches(osmRef)) notFound()
        stored("osm_ref", osmRef)?.let { return it }
        val candidate = nominatim.recent(osmRef) ?: nominatim.lookup(osmRef) ?: notFound()
        return storeOsm(candidate, userId)
    }

    /**
     * A pasted maps link → a place: the name searched near the link's position (then its first word, as names
     * differ between maps), the best match kept by the shared rule; with no match, a place made from the link.
     */
    suspend fun fromLink(text: String, userId: UUID): PlaceDto {
        val card = links.preview(text)
        if (card.needsResolution) throw ApiError(HttpStatusCode.UnprocessableEntity, PlaceError.SHORT_LINK_UNRESOLVED.name)
        val hint = card.place?.takeIf { card.platform == LinkPlatform.GOOGLE_MAPS }
        val name = hint?.name ?: invalid(PlaceError.NOT_A_PLACE_LINK.name)
        val point = hint.coordinates?.let { GeoPoint(it.latitude, it.longitude) }

        if (point != null) {
            val match = PlaceMatching.best(name, point, nominatim.search(name, near = point))
                ?: PlaceMatching.fallbackQuery(name)?.let { PlaceMatching.best(name, point, nominatim.search(it, near = point)) }
            if (match != null) return stored("osm_ref", match.osmRef) ?: storeOsm(match, userId)
        }
        // An imprecise position (the map view's center) is better than none, and the card stays editable.
        point ?: invalid(PlaceError.NOT_A_PLACE_LINK.name)
        stored("link_url", card.canonicalUrl)?.let { return it }
        return db.tx {
            query(
                "insert into place (source, link_url, name, latitude, longitude, created_by) values ('LINK', ?, ?, ?, ?, ?) " +
                    "on conflict (link_url) do update set link_url = excluded.link_url returning $COLUMNS",
                card.canonicalUrl,
                name,
                point.latitude,
                point.longitude,
                userId,
            ) { it.toDto() }.single()
        }
    }

    private suspend fun storeOsm(candidate: PlaceCandidate, userId: UUID): PlaceDto = db.tx {
        query(
            "insert into place (source, osm_ref, name, address, latitude, longitude, created_by) " +
                "values ('OSM', ?, ?, ?, ?, ?, ?) on conflict (osm_ref) do update set osm_ref = excluded.osm_ref returning $COLUMNS",
            candidate.osmRef,
            candidate.name,
            candidate.address,
            candidate.point.latitude,
            candidate.point.longitude,
            userId,
        ) { it.toDto() }.single()
    }

    private suspend fun stored(column: String, value: String): PlaceDto? = db.tx {
        query("select $COLUMNS from place where $column = ?", value) { it.toDto() }.firstOrNull()
    }

    private companion object {
        const val COLUMNS = "id, source, name, address, latitude, longitude, osm_ref, link_url"
    }
}

private fun ResultSet.toDto(): PlaceDto {
    val point = GeoPoint(getDouble(5), getDouble(6))
    return PlaceDto(
        id = getString(1),
        source = PlaceSource.valueOf(getString(2)).name,
        name = getString(3),
        address = getString(4),
        latitude = point.latitude,
        longitude = point.longitude,
        osmRef = getString(7),
        linkUrl = getString(8),
        googleMapsUrl = googleMapsLinkOut(point),
    )
}

fun Route.placeRoutes(auth: Auth, places: Places) {
    get(Routes.PLACE_SEARCH) {
        call.requireUser(auth)
        val results = places.search(call.request.queryParameters["q"].orEmpty())
        call.respond(
            results.map {
                PlaceCandidateDto(it.osmRef, it.name, it.address, it.point.latitude, it.point.longitude, it.category)
            },
        )
    }

    post(Routes.PLACES) {
        val user = call.requireUser(auth)
        call.respond(places.pick(call.receive<PickPlaceRequest>().osmRef, user.userId))
    }

    post(Routes.PLACE_FROM_LINK) {
        val user = call.requireUser(auth)
        call.respond(places.fromLink(call.receive<FromLinkRequest>().text, user.userId))
    }
}
