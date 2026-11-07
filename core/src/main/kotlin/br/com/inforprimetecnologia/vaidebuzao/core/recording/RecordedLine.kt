package br.com.inforprimetecnologia.vaidebuzao.core.recording

import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Polyline
import br.com.inforprimetecnologia.vaidebuzao.core.transit.RoutePattern
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Stop
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork

/**
 * Um sentido de linha gravado pelo usuário dentro do ônibus ("Gravar linha").
 * [trace] = trajeto do GPS; [stops] = posições onde o usuário tocou em "Parou num ponto", em ordem.
 */
data class RecordedLine(
    val id: Long,
    val number: String,
    val name: String?,
    val from: String?,
    val to: String,
    val operator: String?,
    val trace: List<GeoPoint>,
    val stops: List<GeoPoint>,
    /** Só a data (AAAA-MM-DD): o horário exato da viagem não é exportado. */
    val date: String,
) {
    val distanceMeters: Double
        get() = trace.zipWithNext().sumOf { (a, b) -> Geo.distanceMeters(a, b) }
}

object Recording {
    /** Raio para "grudar" um ponto marcado num ponto de ônibus que já existe na base. */
    const val SNAP_METERS = 35.0

    /** Precisão mínima aceitável do GPS para gravar o trajeto. */
    const val MAX_ACCURACY_METERS = 40f

    /** Mantém um ponto do trajeto a cada [minMeters] (reduz ruído e tamanho). */
    fun thin(points: List<GeoPoint>, minMeters: Double = 15.0): List<GeoPoint> {
        if (points.size <= 2) return points
        val out = ArrayList<GeoPoint>(points.size / 2)
        out += points.first()
        for (p in points.subList(1, points.size - 1)) {
            if (Geo.distanceMeters(out.last(), p) >= minMeters) out += p
        }
        out += points.last()
        return out
    }

    /**
     * Simplificação de Ramer–Douglas–Peucker: remove pontos que quase não mudam o desenho
     * (tolerância em metros). Retas longas viram 2 pontos; curvas são preservadas.
     */
    fun simplify(points: List<GeoPoint>, toleranceMeters: Double = 6.0): List<GeoPoint> {
        if (points.size < 3) return points
        val lat0 = Math.toRadians(points.first().lat)
        val kx = 111_320.0 * kotlin.math.cos(lat0)
        val ky = 110_540.0
        val xs = DoubleArray(points.size) { points[it].lon * kx }
        val ys = DoubleArray(points.size) { points[it].lat * ky }
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)
        while (stack.isNotEmpty()) {
            val (i, j) = stack.removeLast()
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            val norm = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-9)
            var best = 0.0
            var idx = -1
            for (k in i + 1 until j) {
                val d = kotlin.math.abs(dy * xs[k] - dx * ys[k] + xs[j] * ys[i] - ys[j] * xs[i]) / norm
                if (d > best) {
                    best = d
                    idx = k
                }
            }
            if (idx > 0 && best > toleranceMeters) {
                keep[idx] = true
                stack.addLast(i to idx)
                stack.addLast(idx to j)
            }
        }
        return points.filterIndexed { k, _ -> keep[k] }
    }

    /** Trajeto pronto para exportar: menos ruído e bem menor. */
    fun exportTrace(trace: List<GeoPoint>): List<GeoPoint> = simplify(thin(trace), 6.0)

    /** Ponto de ônibus da base mais próximo de [p], até [SNAP_METERS]. */
    fun snap(base: TransitNetwork, p: GeoPoint): Stop? =
        base.stopsWithin(p, SNAP_METERS).minByOrNull { it.second }?.let { base.stops[it.first] }

    /**
     * Junta as linhas gravadas à rede da base, para que funcionem na hora (pontos próximos, linhas e rotas).
     * Pontos marcados perto de um ponto existente usam o existente; os demais viram pontos novos (id negativo).
     */
    fun merge(base: TransitNetwork, lines: List<RecordedLine>): TransitNetwork {
        if (lines.isEmpty()) return base
        val newStops = ArrayList<Stop>()
        val patterns = ArrayList<RoutePattern>()
        var nextStopId = -1
        for (line in lines) {
            if (line.stops.size < 2) continue
            val ids = ArrayList<Int>()
            for (p in line.stops) {
                val existing = snap(base, p)
                    ?: newStops.minByOrNull { Geo.distanceMeters(it.point, p) }
                        ?.takeIf { Geo.distanceMeters(it.point, p) <= SNAP_METERS }
                val stop = existing ?: Stop(nextStopId--, null, p, "marcado por você").also { newStops += it }
                if (ids.lastOrNull() != stop.id) ids += stop.id
            }
            if (ids.size < 2) continue
            patterns += RoutePattern(
                id = -(line.id.toInt().coerceAtLeast(1)),
                ref = line.number.trim(),
                name = line.name?.takeIf { it.isNotBlank() } ?: "Linha ${line.number.trim()}",
                from = line.from?.takeIf { it.isNotBlank() },
                to = line.to,
                colorHex = null,
                operator = line.operator?.takeIf { it.isNotBlank() },
                headwayMinutes = null,
                stopIds = ids,
                shape = thin(line.trace).ifEmpty { line.stops },
                inferredStops = false,
                source = RoutePattern.SOURCE_USER,
            )
        }
        if (patterns.isEmpty()) return base
        return TransitNetwork(base.stops + newStops, base.patterns + patterns, base.places, base.meta)
    }

    private fun esc(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    private fun num(d: Double) = "%.6f".format(java.util.Locale.ROOT, d)

    /**
     * Exporta no formato dos arquivos .json de data/linhas (ver data/linhas/LEIA-ME.md), que o pipeline do projeto lê.
     * Vai só o necessário: número, nomes, pontos e trajeto (compactado). Sem horário da viagem.
     */
    fun toProjectJson(line: RecordedLine, appVersion: String): String {
        val sb = StringBuilder()
        sb.append("[{\n")
        sb.append("  \"numero\": \"").append(esc(line.number.trim())).append("\",\n")
        line.name?.takeIf { it.isNotBlank() }?.let { sb.append("  \"nome\": \"").append(esc(it)).append("\",\n") }
        line.operator?.takeIf { it.isNotBlank() }?.let { sb.append("  \"operadora\": \"").append(esc(it)).append("\",\n") }
        sb.append("  \"_origem\": \"Gravado no app Vai de Buzão ").append(esc(appVersion)).append(" em ").append(line.date).append("\",\n")
        sb.append("  \"sentidos\": [{\n")
        line.from?.takeIf { it.isNotBlank() }?.let { sb.append("    \"de\": \"").append(esc(it)).append("\",\n") }
        sb.append("    \"para\": \"").append(esc(line.to)).append("\",\n")
        sb.append("    \"pontos\": [")
        line.stops.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append("\n      {\"lat\": ").append(num(p.lat)).append(", \"lon\": ").append(num(p.lon)).append('}')
        }
        sb.append("\n    ],\n")
        sb.append("    \"trajeto_polyline\": \"").append(esc(Polyline.encode(exportTrace(line.trace)))).append("\"\n")
        sb.append("  }]\n}]\n")
        return sb.toString()
    }

    /** Nome de arquivo seguro: "linha-101-para-cpa.json". */
    fun fileName(line: RecordedLine): String {
        val base = br.com.inforprimetecnologia.vaidebuzao.core.text.TextNormalizer.normalize("linha ${line.number} para ${line.to}")
            .replace(' ', '-').take(60).trim('-')
        return "$base-${line.id}.json"
    }
}
