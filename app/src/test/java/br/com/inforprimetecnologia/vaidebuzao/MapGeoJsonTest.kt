package br.com.inforprimetecnologia.vaidebuzao

import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.transit.RoutePattern
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Stop
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapGeoJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MapGeoJsonTest {
    private val net = TransitNetwork(
        stops = listOf(
            Stop(1, "A", GeoPoint(-15.6, -56.1), null),
            Stop(2, null, GeoPoint(-15.61, -56.09), "Centro"),
        ),
        patterns = listOf(RoutePattern(9, "101", "Linha 101", null, "CPA", null, null, null, listOf(1), emptyList(), false)),
        places = emptyList(),
        meta = null,
    )

    @Test fun pontosViramGeoJsonValido() {
        val root = Json.parseToJsonElement(MapGeoJson.stops(net)).jsonObject
        val features = root["features"]!!.jsonArray
        assertEquals(2, features.size)
        val props = features[0].jsonObject["properties"]!!.jsonObject
        assertEquals("1", props["id"]!!.jsonPrimitive.content)
        assertEquals("true", props["served"]!!.jsonPrimitive.content)
        val coords = features[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(-56.1, coords[0].jsonPrimitive.content.toDouble(), 1e-9) // GeoJSON é [lon, lat]
    }

    @Test fun linhasIgnoramTrajetoComUmPonto() {
        val json = MapGeoJson.lines(listOf(listOf(GeoPoint(0.0, 0.0)) to "#000000", listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)) to "#FFFFFF"))
        val features = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray
        assertEquals(1, features.size)
    }
}
