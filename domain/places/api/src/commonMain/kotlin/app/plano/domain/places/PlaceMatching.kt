package app.plano.domain.places

/**
 * Is this OpenStreetMap result the place a Google Maps link points to? (PRD step 4: search the name near the
 * link's position, keep the nearest good match, else create the place from the link.)
 * Names differ between the two maps: Google's « Pathé Île Seguin » is « Pathé » in OpenStreetMap, 57 m away.
 */
object PlaceMatching {
    const val MAX_DISTANCE_METERS = 300.0

    /** Half the box searched around the link's position, in degrees of latitude (about 550 m). */
    const val SEARCH_RADIUS_DEGREES = 0.005

    // Areas, not venues: « Île Seguin » would match « Pathé Île Seguin » by its words.
    private val AREA_CATEGORIES = setOf("boundary", "place", "landuse", "highway", "natural", "waterway", "railway")
    private val STOP_WORDS = setOf("le", "la", "les", "l", "de", "du", "des", "d", "chez", "au", "aux", "the", "et", "and", "a")

    fun best(name: String, near: GeoPoint, candidates: List<PlaceCandidate>): PlaceCandidate? {
        val wanted = tokens(name)
        if (wanted.isEmpty()) return null
        return candidates.asSequence()
            .filter { it.category !in AREA_CATEGORIES }
            .map { Triple(it, similarity(wanted, tokens(it.name)), it.point.distanceMeters(near)) }
            .filter { (_, score, distance) -> score >= MIN_SCORE && distance <= MAX_DISTANCE_METERS }
            .sortedWith(compareByDescending<Triple<PlaceCandidate, Double, Double>> { it.second }.thenBy { it.third })
            .firstOrNull()
            ?.first
    }

    /** The second search when the full name finds nothing: its first meaningful word (« Pathé »). */
    fun fallbackQuery(name: String): String? {
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.size < 2) return null
        return words.firstOrNull { normalize(it) !in STOP_WORDS && it.length >= 3 }
    }

    /** 1 for the same words, 0.8 when one name's words are all in the other, else the share of common words. */
    internal fun similarity(a: Set<String>, b: Set<String>): Double = when {
        a.isEmpty() || b.isEmpty() -> 0.0
        a == b -> 1.0
        a.containsAll(b) || b.containsAll(a) -> 0.8
        else -> a.intersect(b).size.toDouble() / a.union(b).size
    }

    internal fun tokens(name: String): Set<String> =
        normalize(name).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in STOP_WORDS }.toSet()

    private const val MIN_SCORE = 0.5

    private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿœæ"
    private const val PLAIN = "aaaaaaceeeeiiiinooooouuuuyyoa"

    /** Lowercase, no accents (no `java.text.Normalizer` in common code: a table of Latin letters is enough here). */
    internal fun normalize(text: String): String = buildString {
        for (c in text.lowercase()) {
            val i = ACCENTED.indexOf(c)
            append(if (i >= 0) PLAIN[i] else c)
        }
    }
}
