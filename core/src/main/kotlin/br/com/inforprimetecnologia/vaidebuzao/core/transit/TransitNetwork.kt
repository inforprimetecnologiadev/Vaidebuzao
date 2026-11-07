package br.com.inforprimetecnologia.vaidebuzao.core.transit

import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.text.TextNormalizer
import kotlin.math.cos
import kotlin.math.floor

/** Ponto encontrado perto de uma coordenada. */
data class NearbyStop(val stop: Stop, val distanceMeters: Double, val walkMinutes: Double)

/** Ocorrência de um ponto dentro de um sentido de linha. */
data class StopVisit(val patternIndex: Int, val position: Int)

/** Resultado de busca textual offline. */
sealed interface SearchHit {
    val title: String
    val point: GeoPoint

    data class PlaceHit(val place: Place) : SearchHit {
        override val title get() = place.name
        override val point get() = place.point
    }

    data class StopHit(val stop: Stop) : SearchHit {
        override val title get() = stop.displayName
        override val point get() = stop.point
    }
}

/**
 * Rede de transporte em memória, com índice espacial em grade para achar pontos próximos rapidamente.
 * Imutável e segura para uso entre threads.
 */
class TransitNetwork(
    val stops: List<Stop>,
    val patterns: List<RoutePattern>,
    val places: List<Place>,
    val meta: TransitMeta?,
) {
    val stopIndexById: Map<Int, Int> = stops.withIndex().associate { (i, s) -> s.id to i }
    val patternIndexById: Map<Int, Int> = patterns.withIndex().associate { (i, p) -> p.id to i }

    /** Para cada ponto (índice), as passagens de linhas (sentido + posição). */
    val visitsByStop: Array<List<StopVisit>>

    /** Sequência de índices de pontos por sentido. */
    val patternStopIdx: Array<IntArray>

    val lines: List<Line>

    private val cellSizeDeg = 0.005
    private val grid = HashMap<Long, MutableList<Int>>()

    private val normalizedStopNames: Array<String>
    private val normalizedPlaceNames: Array<String>

    init {
        val visits = Array(stops.size) { ArrayList<StopVisit>(2) }
        patternStopIdx = Array(patterns.size) { pi ->
            patterns[pi].stopIds.mapNotNull { stopIndexById[it] }.toIntArray()
        }
        patternStopIdx.forEachIndexed { pi, seq ->
            seq.forEachIndexed { pos, si -> visits[si].add(StopVisit(pi, pos)) }
        }
        visitsByStop = Array(stops.size) { visits[it] }

        stops.forEachIndexed { i, s -> grid.getOrPut(cellKey(s.point.lat, s.point.lon)) { ArrayList() }.add(i) }

        lines = patterns.groupBy { it.ref.ifBlank { it.name } }
            .map { (ref, list) ->
                Line(
                    ref = ref,
                    name = list.first().name,
                    colorHex = list.firstNotNullOfOrNull { it.colorHex },
                    patterns = list.sortedBy { it.to ?: it.name },
                )
            }
            .sortedWith(compareBy<Line>({ it.ref.toIntOrNull() ?: Int.MAX_VALUE }, { it.ref }))

        normalizedStopNames = Array(stops.size) { TextNormalizer.normalize(stops[it].name ?: "") }
        normalizedPlaceNames = Array(places.size) { TextNormalizer.normalize(places[it].name) }
    }

    val isEmpty: Boolean get() = stops.isEmpty()

    private fun cellKey(lat: Double, lon: Double): Long {
        val y = floor(lat / cellSizeDeg).toLong()
        val x = floor(lon / cellSizeDeg).toLong()
        return (y shl 32) xor (x and 0xffffffffL)
    }

    /** Índices de pontos dentro de [radiusMeters] de [point], com a distância geodésica. */
    fun stopsWithin(point: GeoPoint, radiusMeters: Double): List<Pair<Int, Double>> {
        val dLat = radiusMeters / 111_320.0
        val dLon = radiusMeters / (111_320.0 * cos(Math.toRadians(point.lat)).coerceAtLeast(0.01))
        val y0 = floor((point.lat - dLat) / cellSizeDeg).toLong()
        val y1 = floor((point.lat + dLat) / cellSizeDeg).toLong()
        val x0 = floor((point.lon - dLon) / cellSizeDeg).toLong()
        val x1 = floor((point.lon + dLon) / cellSizeDeg).toLong()
        val out = ArrayList<Pair<Int, Double>>()
        for (y in y0..y1) for (x in x0..x1) {
            val cell = grid[(y shl 32) xor (x and 0xffffffffL)] ?: continue
            for (i in cell) {
                val d = Geo.distanceMeters(point, stops[i].point)
                if (d <= radiusMeters) out += i to d
            }
        }
        return out
    }

    /**
     * Pontos mais próximos. Procura em raios crescentes até achar [limit] pontos ou chegar a [maxRadiusMeters].
     * Pontos com linhas conhecidas aparecem antes dos que não têm linha quando [preferServed] é true.
     */
    fun nearestStops(
        point: GeoPoint,
        limit: Int = 8,
        maxRadiusMeters: Double = 3000.0,
        preferServed: Boolean = true,
    ): List<NearbyStop> {
        var radius = 400.0
        var found: List<Pair<Int, Double>> = emptyList()
        while (radius <= maxRadiusMeters) {
            found = stopsWithin(point, radius)
            if (found.size >= limit) break
            radius *= 2
        }
        if (found.size < limit) found = stopsWithin(point, maxRadiusMeters)
        val sorted = if (preferServed) {
            // pontos sem linha só entram depois, a menos que estejam muito mais perto
            found.sortedBy { (i, d) -> if (visitsByStop[i].isEmpty()) d + 250 else d }
        } else {
            found.sortedBy { it.second }
        }
        return sorted.take(limit).map { (i, d) ->
            val walk = d * Geo.WALK_DETOUR_FACTOR
            NearbyStop(stops[i], d, Geo.walkMinutes(walk))
        }
    }

    fun stopById(id: Int): Stop? = stopIndexById[id]?.let { stops[it] }

    fun patternById(id: Int): RoutePattern? = patternIndexById[id]?.let { patterns[it] }

    fun lineByRef(ref: String): Line? = lines.firstOrNull { it.ref == ref }

    /** Sentidos de linhas que passam no ponto, sem repetição. */
    fun patternsAtStop(stopId: Int): List<RoutePattern> {
        val si = stopIndexById[stopId] ?: return emptyList()
        return visitsByStop[si].map { patterns[it.patternIndex] }.distinctBy { it.id }
            .sortedWith(compareBy({ it.ref.toIntOrNull() ?: Int.MAX_VALUE }, { it.ref }, { it.to ?: "" }))
    }

    fun searchLines(query: String, limit: Int = 50): List<Line> {
        val q = TextNormalizer.normalize(query)
        if (q.isBlank()) return lines.take(limit)
        return lines.filter { line ->
            TextNormalizer.normalize(line.ref).startsWith(q) ||
                TextNormalizer.matches(TextNormalizer.normalize(line.name), q) ||
                line.patterns.any { p ->
                    TextNormalizer.matches(TextNormalizer.normalize("${p.from ?: ""} ${p.to ?: ""}"), q)
                }
        }.take(limit)
    }

    /** Busca offline em lugares conhecidos e nomes de pontos. Lugares primeiro. */
    fun searchPlaces(query: String, near: GeoPoint? = null, limit: Int = 12): List<SearchHit> {
        val q = TextNormalizer.normalize(query)
        if (q.length < 2) return emptyList()
        val placeHits = places.indices.filter { TextNormalizer.matches(normalizedPlaceNames[it], q) }
            .map { SearchHit.PlaceHit(places[it]) }
        val stopHits = stops.indices.filter { TextNormalizer.matches(normalizedStopNames[it], q) }
            .map { SearchHit.StopHit(stops[it]) }
            .distinctBy { it.stop.name }
        val sorter: (SearchHit) -> Double = { hit ->
            val starts = if (TextNormalizer.normalize(hit.title).startsWith(q)) 0.0 else 50_000.0
            starts + (near?.let { Geo.distanceMeters(it, hit.point) } ?: 0.0)
        }
        return (placeHits.sortedBy(sorter) + stopHits.sortedBy(sorter)).take(limit)
    }

    companion object {
        val EMPTY = TransitNetwork(emptyList(), emptyList(), emptyList(), null)
    }
}
