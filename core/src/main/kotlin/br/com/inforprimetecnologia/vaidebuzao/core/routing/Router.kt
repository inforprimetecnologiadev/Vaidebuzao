package br.com.inforprimetecnologia.vaidebuzao.core.routing

import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.transit.RoutePattern
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Stop
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork

/** Parâmetros do cálculo de rotas. Os tempos são ESTIMATIVAS (não há horário oficial). */
data class RouterConfig(
    /** Raio (linha reta) para procurar pontos de embarque perto da origem e do destino. */
    val maxAccessMeters: Double = 900.0,
    /** Raio (linha reta) para baldeação a pé entre pontos diferentes. */
    val maxTransferMeters: Double = 350.0,
    /** Número máximo de ônibus numa viagem (3 = até 2 baldeações). */
    val maxRides: Int = 3,
    /** Velocidade média do ônibus no trânsito urbano: 17 km/h. */
    val busMetersPerMinute: Double = 17_000.0 / 60.0,
    /** O ônibus não anda em linha reta entre pontos. */
    val busDetourFactor: Double = 1.15,
    /** Tempo parado em cada ponto (embarque/desembarque). */
    val dwellMinutesPerStop: Double = 0.4,
    /** Intervalo padrão entre ônibus quando a fonte não informa. */
    val defaultHeadwayMinutes: Int = 20,
    /** Penalidade usada só para preferir menos baldeações. */
    val transferPenaltyMinutes: Double = 3.0,
    /** Até essa distância a pé, também sugerimos ir caminhando. */
    val maxDirectWalkMeters: Double = 1500.0,
    val maxOptions: Int = 6,
)

sealed interface Leg {
    val minutes: Double

    data class Walk(
        val from: GeoPoint,
        val to: GeoPoint,
        val fromName: String,
        val toName: String,
        val meters: Double,
    ) : Leg {
        override val minutes: Double get() = Geo.walkMinutes(meters)
    }

    data class Ride(
        val pattern: RoutePattern,
        /** Pontos do embarque ao desembarque, inclusive. */
        val stops: List<Stop>,
        val waitMinutes: Double,
        val rideMinutes: Double,
    ) : Leg {
        val board: Stop get() = stops.first()
        val alight: Stop get() = stops.last()
        val stopCount: Int get() = stops.size - 1
        override val minutes: Double get() = waitMinutes + rideMinutes
    }
}

data class Itinerary(val legs: List<Leg>) {
    val rides: List<Leg.Ride> get() = legs.filterIsInstance<Leg.Ride>()
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)
    val walkMeters: Double get() = legs.filterIsInstance<Leg.Walk>().sumOf { it.meters }
    val totalMinutes: Double get() = legs.sumOf { it.minutes }
    val isWalkOnly: Boolean get() = rides.isEmpty()
    val signature: String get() = if (isWalkOnly) "walk" else rides.joinToString(">") { it.pattern.id.toString() }
}

/**
 * Roteador offline inspirado no RAPTOR (Round-bAsed Public Transit Optimized Router), adaptado para uma
 * rede sem horários: cada sentido de linha é percorrido com tempo de espera = metade do intervalo.
 * Cada "rodada" acrescenta um ônibus, o que dá naturalmente opções com 0, 1 ou 2 baldeações.
 */
class Router(private val net: TransitNetwork, private val cfg: RouterConfig = RouterConfig()) {

    /** Minutos acumulados de viagem até cada posição do sentido. */
    private val cumulative: Array<DoubleArray> = Array(net.patterns.size) { pi ->
        val seq = net.patternStopIdx[pi]
        val out = DoubleArray(seq.size)
        for (pos in 1 until seq.size) {
            val d = Geo.distanceMeters(net.stops[seq[pos - 1]].point, net.stops[seq[pos]].point)
            out[pos] = out[pos - 1] + d * cfg.busDetourFactor / cfg.busMetersPerMinute + cfg.dwellMinutesPerStop
        }
        out
    }

    private fun waitMinutes(pi: Int): Double =
        (net.patterns[pi].headwayMinutes ?: cfg.defaultHeadwayMinutes) / 2.0

    private fun walkMin(geodesicMeters: Double) = Geo.walkMinutes(geodesicMeters * Geo.WALK_DETOUR_FACTOR)

    fun route(origin: GeoPoint, originName: String, destination: GeoPoint, destinationName: String): List<Itinerary> {
        val options = ArrayList<Itinerary>()
        if (!net.isEmpty) {
            val access = net.stopsWithin(origin, cfg.maxAccessMeters)
            val egress = net.stopsWithin(destination, cfg.maxAccessMeters)
            if (access.isNotEmpty() && egress.isNotEmpty()) {
                options += directOptions(access, egress, origin, originName, destination, destinationName)
                options += raptor(access, egress, origin, originName, destination, destinationName)
            }
        }
        val rideOptions = filterDominated(options.distinctBy { it.signature })
            .sortedBy { it.totalMinutes + it.transfers * cfg.transferPenaltyMinutes }
            .take(cfg.maxOptions)

        val walkMeters = Geo.walkMeters(origin, destination)
        val walkOnly = if (walkMeters <= cfg.maxDirectWalkMeters) {
            listOf(Itinerary(listOf(Leg.Walk(origin, destination, originName, destinationName, walkMeters))))
        } else {
            emptyList()
        }
        return (rideOptions + walkOnly).sortedBy { it.totalMinutes + it.transfers * cfg.transferPenaltyMinutes }
    }

    /** Todas as linhas diretas (sem baldeação), a melhor combinação embarque/desembarque de cada sentido. */
    private fun directOptions(
        access: List<Pair<Int, Double>>,
        egress: List<Pair<Int, Double>>,
        origin: GeoPoint,
        originName: String,
        destination: GeoPoint,
        destinationName: String,
    ): List<Itinerary> {
        val egressWalk = HashMap<Int, Double>().apply { egress.forEach { (i, d) -> put(i, d) } }
        data class Best(val total: Double, val pi: Int, val bp: Int, val ap: Int, val accessD: Double, val egressD: Double)
        val bestByPattern = HashMap<Int, Best>()
        for ((a, accessD) in access) {
            for (visit in net.visitsByStop[a]) {
                val pi = visit.patternIndex
                val seq = net.patternStopIdx[pi]
                val cum = cumulative[pi]
                for (pos in visit.position + 1 until seq.size) {
                    val eD = egressWalk[seq[pos]] ?: continue
                    val total = walkMin(accessD) + waitMinutes(pi) + (cum[pos] - cum[visit.position]) + walkMin(eD)
                    val cur = bestByPattern[pi]
                    if (cur == null || total < cur.total) {
                        bestByPattern[pi] = Best(total, pi, visit.position, pos, accessD, eD)
                    }
                }
            }
        }
        // Um resultado por linha (ref): o sentido/embarque mais rápido.
        return bestByPattern.values
            .groupBy { net.patterns[it.pi].ref.ifBlank { it.pi.toString() } }
            .map { (_, list) -> list.minBy { it.total } }
            .sortedBy { it.total }
            .take(cfg.maxOptions)
            .map { b ->
                val seq = net.patternStopIdx[b.pi]
                val board = net.stops[seq[b.bp]]
                val alight = net.stops[seq[b.ap]]
                Itinerary(
                    listOf(
                        Leg.Walk(origin, board.point, originName, board.displayName, b.accessD * Geo.WALK_DETOUR_FACTOR),
                        rideLeg(b.pi, b.bp, b.ap),
                        Leg.Walk(alight.point, destination, alight.displayName, destinationName, b.egressD * Geo.WALK_DETOUR_FACTOR),
                    ),
                )
            }
    }

    private fun rideLeg(pi: Int, bp: Int, ap: Int): Leg.Ride {
        val seq = net.patternStopIdx[pi]
        return Leg.Ride(
            pattern = net.patterns[pi],
            stops = (bp..ap).map { net.stops[seq[it]] },
            waitMinutes = waitMinutes(pi),
            rideMinutes = cumulative[pi][ap] - cumulative[pi][bp],
        )
    }

    private fun raptor(
        access: List<Pair<Int, Double>>,
        egress: List<Pair<Int, Double>>,
        origin: GeoPoint,
        originName: String,
        destination: GeoPoint,
        destinationName: String,
    ): List<Itinerary> {
        val n = net.stops.size
        val rounds = cfg.maxRides
        val inf = Double.POSITIVE_INFINITY
        val label = Array(rounds + 1) { DoubleArray(n) { inf } }
        val best = DoubleArray(n) { inf }
        val kind = Array(rounds + 1) { ByteArray(n) }
        val pPattern = Array(rounds + 1) { IntArray(n) }
        val pBoard = Array(rounds + 1) { IntArray(n) }
        val pAlight = Array(rounds + 1) { IntArray(n) }
        val pFrom = Array(rounds + 1) { IntArray(n) }

        var marked = ArrayList<Int>()
        for ((i, d) in access) {
            val t = walkMin(d)
            if (t < label[0][i]) {
                label[0][i] = t
                best[i] = t
                kind[0][i] = ACCESS
                marked += i
            }
        }
        val egressMap = HashMap<Int, Double>().apply { egress.forEach { (i, d) -> put(i, d) } }
        var bestTarget = inf

        for (k in 1..rounds) {
            val queue = HashMap<Int, Int>()
            for (s in marked) for (v in net.visitsByStop[s]) {
                val cur = queue[v.patternIndex]
                if (cur == null || v.position < cur) queue[v.patternIndex] = v.position
            }
            val rideMarked = ArrayList<Int>()
            val penalty = if (k > 1) cfg.transferPenaltyMinutes else 0.0
            for ((pi, start) in queue) {
                val seq = net.patternStopIdx[pi]
                val cum = cumulative[pi]
                val wait = waitMinutes(pi)
                var boardPos = -1
                var boardTime = inf
                for (pos in start until seq.size) {
                    val s = seq[pos]
                    if (boardPos >= 0) {
                        val arr = boardTime + (cum[pos] - cum[boardPos])
                        if (arr < best[s] && arr < bestTarget) {
                            if (label[k][s] == inf) rideMarked += s
                            label[k][s] = arr
                            best[s] = arr
                            kind[k][s] = RIDE
                            pPattern[k][s] = pi
                            pBoard[k][s] = boardPos
                            pAlight[k][s] = pos
                        }
                    }
                    val prev = label[k - 1][s]
                    if (prev < inf) {
                        val cand = prev + wait + penalty
                        if (boardPos < 0 || cand < boardTime + (cum[pos] - cum[boardPos])) {
                            boardPos = pos
                            boardTime = cand
                        }
                    }
                }
            }
            for ((e, d) in egressMap) {
                val t = label[k][e] + walkMin(d)
                if (t < bestTarget) bestTarget = t
            }
            // Baldeação a pé entre pontos próximos
            val newMarked = ArrayList<Int>(rideMarked)
            for (s in rideMarked) {
                if (kind[k][s] != RIDE) continue
                for ((t, d) in net.stopsWithin(net.stops[s].point, cfg.maxTransferMeters)) {
                    if (t == s) continue
                    val arr = label[k][s] + walkMin(d)
                    if (arr < best[t]) {
                        if (label[k][t] == inf) newMarked += t
                        label[k][t] = arr
                        best[t] = arr
                        kind[k][t] = TRANSFER
                        pFrom[k][t] = s
                    }
                }
            }
            marked = newMarked
            if (marked.isEmpty()) break
        }

        val out = ArrayList<Itinerary>()
        for (k in 1..rounds) {
            val candidates = egressMap.entries
                .filter { kind[k][it.key] == RIDE }
                .sortedBy { label[k][it.key] + walkMin(it.value) }
                .take(3)
            for ((e, d) in candidates) {
                val legs = reconstruct(k, e, kind, pPattern, pBoard, pAlight, pFrom, origin, originName) ?: continue
                val alight = net.stops[e]
                legs += Leg.Walk(alight.point, destination, alight.displayName, destinationName, d * Geo.WALK_DETOUR_FACTOR)
                out += Itinerary(mergeWalks(legs))
            }
        }
        return out
    }

    private fun reconstruct(
        k: Int,
        e: Int,
        kind: Array<ByteArray>,
        pPattern: Array<IntArray>,
        pBoard: Array<IntArray>,
        pAlight: Array<IntArray>,
        pFrom: Array<IntArray>,
        origin: GeoPoint,
        originName: String,
    ): MutableList<Leg>? {
        val legs = ArrayDeque<Leg>()
        var round = k
        var s = e
        var guard = 0
        while (guard++ < 64) {
            when (kind[round][s]) {
                RIDE -> {
                    val pi = pPattern[round][s]
                    val bp = pBoard[round][s]
                    val ap = pAlight[round][s]
                    legs.addFirst(rideLeg(pi, bp, ap))
                    s = net.patternStopIdx[pi][bp]
                    round -= 1
                    if (round < 0) return null
                }
                TRANSFER -> {
                    val from = pFrom[round][s]
                    val a = net.stops[from]
                    val b = net.stops[s]
                    legs.addFirst(Leg.Walk(a.point, b.point, a.displayName, b.displayName, Geo.walkMeters(a.point, b.point)))
                    s = from
                }
                ACCESS -> {
                    val stop = net.stops[s]
                    legs.addFirst(Leg.Walk(origin, stop.point, originName, stop.displayName, Geo.walkMeters(origin, stop.point)))
                    return legs.toMutableList()
                }
                else -> return null
            }
        }
        return null
    }

    private fun mergeWalks(legs: List<Leg>): List<Leg> {
        val out = ArrayList<Leg>()
        for (leg in legs) {
            val last = out.lastOrNull()
            if (leg is Leg.Walk && last is Leg.Walk) {
                out[out.size - 1] = Leg.Walk(last.from, leg.to, last.fromName, leg.toName, last.meters + leg.meters)
            } else {
                out += leg
            }
        }
        return out
    }

    /** Remove opções claramente piores: mais baldeações e mais lentas, ou bem mais lentas com as mesmas baldeações. */
    private fun filterDominated(options: List<Itinerary>): List<Itinerary> =
        options.filter { b ->
            options.none { a ->
                a !== b && (
                    (a.transfers < b.transfers && a.totalMinutes <= b.totalMinutes) ||
                        (a.transfers == b.transfers && a.totalMinutes + SAME_TRANSFERS_SLACK_MIN < b.totalMinutes)
                    )
            }
        }

    private companion object {
        const val ACCESS: Byte = 1
        const val RIDE: Byte = 2
        const val TRANSFER: Byte = 3
        const val SAME_TRANSFERS_SLACK_MIN = 15.0
    }
}
