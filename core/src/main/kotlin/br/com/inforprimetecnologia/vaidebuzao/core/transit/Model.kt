package br.com.inforprimetecnologia.vaidebuzao.core.transit

import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint

/** Ponto de ônibus. [name] pode ser nulo quando a fonte de dados não informa nome. */
data class Stop(
    val id: Int,
    val name: String?,
    val point: GeoPoint,
    /** Bairro/localidade mais próximo, usado quando não há nome. */
    val area: String?,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: area?.let { "Ponto de ônibus · $it" } ?: "Ponto de ônibus"
}

/**
 * Um sentido/variante de uma linha (ex.: linha 101, sentido Centro -> CPA).
 * [stopIds] em ordem de passagem; [shape] é o desenho do trajeto.
 */
data class RoutePattern(
    val id: Int,
    val ref: String,
    val name: String,
    val from: String?,
    val to: String?,
    val colorHex: String?,
    val operator: String?,
    /** Intervalo médio entre ônibus (min), quando a fonte informa. */
    val headwayMinutes: Int?,
    val stopIds: List<Int>,
    val shape: List<GeoPoint>,
    /** true quando a sequência de pontos foi inferida pela proximidade ao trajeto. */
    val inferredStops: Boolean,
) {
    val directionLabel: String
        get() = when {
            !to.isNullOrBlank() -> "Sentido $to"
            else -> name
        }
}

/** Lugar conhecido para busca offline (bairro, terminal, hospital, universidade...). */
data class Place(
    val id: Int,
    val name: String,
    val kind: String,
    val point: GeoPoint,
)

/** Metadados da base de dados de transporte embutida. */
data class TransitMeta(
    val dataVersion: String,
    val generatedAt: String,
    val source: String,
)

/** Linha agrupada por número (ref), com seus sentidos. */
data class Line(
    val ref: String,
    val name: String,
    val colorHex: String?,
    val patterns: List<RoutePattern>,
)
