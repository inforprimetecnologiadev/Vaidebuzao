package br.com.inforprimetecnologia.vaidebuzao.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Coordenada geográfica (WGS-84). */
data class GeoPoint(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Velocidade média de caminhada: 4,5 km/h = 75 m/min. */
    const val WALK_METERS_PER_MIN = 75.0

    /** Ruas não são linha reta: fator de correção aplicado à distância geodésica a pé. */
    const val WALK_DETOUR_FACTOR = 1.3

    /** Distância geodésica (Haversine) em metros. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Distância estimada a pé (com fator de correção). */
    fun walkMeters(a: GeoPoint, b: GeoPoint): Double = distanceMeters(a, b) * WALK_DETOUR_FACTOR

    fun walkMinutes(walkMeters: Double): Double = walkMeters / WALK_METERS_PER_MIN

    /** Texto amigável: "350 m" ou "1,2 km". */
    fun formatDistance(meters: Double): String =
        if (meters < 1000) {
            "${(meters / 10).roundToInt() * 10} m"
        } else {
            val km = (meters / 100).roundToInt() / 10.0
            km.toString().replace('.', ',') + " km"
        }

    /** Texto amigável de minutos: "1 min", "12 min", "1 h 05 min". */
    fun formatMinutes(minutes: Double): String {
        val total = minutes.roundToInt().coerceAtLeast(1)
        return if (total < 60) "$total min" else "${total / 60} h ${(total % 60).toString().padStart(2, '0')} min"
    }
}

/** Codificação de polilinha (algoritmo público "Encoded Polyline", precisão 1e5). */
object Polyline {
    fun decode(encoded: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out += GeoPoint(lat / 1e5, lon / 1e5)
        }
        return out
    }

    fun encode(points: List<GeoPoint>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLon = 0
        for (p in points) {
            val lat = (p.lat * 1e5).roundToInt()
            val lon = (p.lon * 1e5).roundToInt()
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
