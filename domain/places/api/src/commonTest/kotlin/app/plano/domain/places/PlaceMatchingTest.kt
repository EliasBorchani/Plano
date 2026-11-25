package app.plano.domain.places

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Candidates as the public Nominatim returned them on 2026-11-25, around the owner's sample links.
private val patheLink = GeoPoint(48.8221715, 2.2375611)
private val crossfitLink = GeoPoint(48.826446, 2.240368)

private fun candidate(ref: String, name: String, lat: Double, lng: Double, category: String = "amenity") =
    PlaceCandidate(ref, name, null, GeoPoint(lat, lng), category)

class PlaceMatchingTest {
    @Test
    fun distances() {
        val pathe = GeoPoint(48.8221715, 2.2375611)
        val cinema = GeoPoint(48.8226, 2.2382)
        assertTrue(pathe.distanceMeters(cinema) in 55.0..75.0)
        // Paris to Lyon, about 392 km.
        assertTrue(GeoPoint(48.8566, 2.3522).distanceMeters(GeoPoint(45.764, 4.8357)) in 390_000.0..394_000.0)
    }

    @Test
    fun aShorterOsmNameNearbyMatches() {
        val pathe = candidate("N14003653721", "Pathé", 48.82266, 2.23794)
        val island = candidate("R1", "Île Seguin", 48.8235, 2.2355, category = "place")
        assertEquals(pathe, PlaceMatching.best("Pathé Île Seguin", patheLink, listOf(island, pathe)))
    }

    @Test
    fun theRightTrapezeAmongNeighbours() {
        val candidates = listOf(
            candidate("W1053563399", "Ouvrage Trapèze", 48.8290, 2.2420, category = "landuse"),
            candidate("N2680532896", "Médiathèque Le Trapèze", 48.8284, 2.2400),
            candidate("N4604333701", "Boucherie du Trapèze", 48.8255, 2.2394),
            candidate("N6790321285", "Crossfit le Trapèze", 48.82656, 2.24030, category = "leisure"),
            candidate("N3859257033", "Pharmacie du Trapèze", 48.8270, 2.2420),
        )
        assertEquals("N6790321285", PlaceMatching.best("CrossFit Le Trapèze", crossfitLink, candidates)?.osmRef)
    }

    @Test
    fun noMatchWhenFarOrDifferent() {
        val farPathe = candidate("N802009153", "Pathé Boulogne", 48.8377591, 2.2393726)
        assertNull(PlaceMatching.best("Pathé Île Seguin", patheLink, listOf(farPathe)))
        val neighbour = candidate("N1", "Boulangerie Paul", 48.8222, 2.2376)
        assertNull(PlaceMatching.best("Pathé Île Seguin", patheLink, listOf(neighbour)))
        assertNull(PlaceMatching.best("Le", patheLink, listOf(neighbour)))
    }

    @Test
    fun fallbackQueryIsTheFirstMeaningfulWord() {
        assertEquals("Pathé", PlaceMatching.fallbackQuery("Pathé Île Seguin"))
        assertEquals("Petit", PlaceMatching.fallbackQuery("Le Petit Bistrot"))
        assertEquals("4PADEL", PlaceMatching.fallbackQuery("4PADEL Boulogne-Billancourt"))
        assertNull(PlaceMatching.fallbackQuery("Pathé"))
    }

    @Test
    fun queries() {
        assertEquals(PlaceError.QUERY_TOO_SHORT, PlaceQuery.validate(" a "))
        assertEquals(PlaceError.QUERY_TOO_LONG, PlaceQuery.validate("x".repeat(201)))
        assertNull(PlaceQuery.validate("Pathé"))
    }
}
