package br.com.inforprimetecnologia.vaidebuzao.ui.map

import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork

/** Monta GeoJSON em texto (sem dependências extras) para as camadas do mapa. */
object MapGeoJson {
    private fun StringBuilder.coord(p: GeoPoint) = append('[').append(p.lon).append(',').append(p.lat).append(']')

    fun stops(net: TransitNetwork): String {
        val sb = StringBuilder(net.stops.size * 80)
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        net.stops.forEachIndexed { i, s ->
            if (i > 0) sb.append(',')
            val served = net.visitsByStop[i].isNotEmpty()
            sb.append("{\"type\":\"Feature\",\"properties\":{\"id\":").append(s.id)
                .append(",\"served\":").append(served).append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
            sb.coord(s.point)
            sb.append("}}")
        }
        return sb.append("]}").toString()
    }

    fun points(points: List<Pair<GeoPoint, String>>): String {
        val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
        points.forEachIndexed { i, (p, color) ->
            if (i > 0) sb.append(',')
            sb.append("{\"type\":\"Feature\",\"properties\":{\"color\":\"").append(color)
                .append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
            sb.coord(p)
            sb.append("}}")
        }
        return sb.append("]}").toString()
    }

    fun lines(lines: List<Pair<List<GeoPoint>, String>>): String {
        val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        for ((pts, color) in lines) {
            if (pts.size < 2) continue
            if (!first) sb.append(',')
            first = false
            sb.append("{\"type\":\"Feature\",\"properties\":{\"color\":\"").append(color)
                .append("\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            pts.forEachIndexed { i, p ->
                if (i > 0) sb.append(',')
                sb.coord(p)
            }
            sb.append("]}}")
        }
        return sb.append("]}").toString()
    }

    const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"
}
