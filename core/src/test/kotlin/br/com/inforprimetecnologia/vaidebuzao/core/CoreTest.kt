package br.com.inforprimetecnologia.vaidebuzao.core

import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentAction
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentClock
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentRecord
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentRules
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentStatus
import br.com.inforprimetecnologia.vaidebuzao.core.consent.Hashing
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Polyline
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Leg
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Router
import br.com.inforprimetecnologia.vaidebuzao.core.text.TextNormalizer
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Place
import br.com.inforprimetecnologia.vaidebuzao.core.transit.RoutePattern
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Stop
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun distanciaCuiabaVarzeaGrandeAproximada() {
        val pracaAlencastro = GeoPoint(-15.5960, -56.0960)
        val centroVg = GeoPoint(-15.6460, -56.1320)
        val d = Geo.distanceMeters(pracaAlencastro, centroVg)
        assertTrue("distância $d", d in 6_000.0..7_000.0)
    }

    @Test fun polylineIdaEVolta() {
        val pts = listOf(GeoPoint(-15.6, -56.1), GeoPoint(-15.61234, -56.09876), GeoPoint(-15.5, -56.2))
        val back = Polyline.decode(Polyline.encode(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-5)
            assertEquals(a.lon, b.lon, 1e-5)
        }
    }

    @Test fun formatacao() {
        assertEquals("350 m", Geo.formatDistance(347.0))
        assertEquals("1,2 km", Geo.formatDistance(1234.0))
        assertEquals("12 min", Geo.formatMinutes(12.2))
        assertEquals("1 h 05 min", Geo.formatMinutes(65.0))
    }

    @Test fun normalizacaoDeTexto() {
        assertEquals("varzea grande", TextNormalizer.normalize("Várzea-Grande"))
        assertTrue(TextNormalizer.matches("terminal andre maggi", "term and"))
        assertFalse(TextNormalizer.matches("terminal andre maggi", "cpa"))
    }
}

/** Rede fictícia: duas linhas que se cruzam no ponto 3 (baldeação). */
object FakeNet {
    // Linha A anda para leste em lat -15.60; linha B anda para norte em lon -56.06.
    private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon)
    val stops = listOf(
        Stop(1, "A1", p(-15.600, -56.100), "Centro"),
        Stop(2, "A2", p(-15.600, -56.090), "Centro"),
        Stop(3, "Cruzamento", p(-15.600, -56.060), "Bairro"),
        Stop(4, "A4", p(-15.600, -56.040), "Leste"),
        Stop(5, "B1", p(-15.580, -56.060), "Norte"),
        Stop(6, "B2", p(-15.560, -56.060), "Norte"),
        Stop(7, "Isolado", p(-15.700, -56.300), "Longe"),
    )
    val a = RoutePattern(10, "101", "Linha 101", "Centro", "Leste", "#1446C8", null, 20, listOf(1, 2, 3, 4), emptyList(), false)
    val b = RoutePattern(20, "202", "Linha 202", "Bairro", "Norte", null, null, 10, listOf(3, 5, 6), emptyList(), false)
    val network = TransitNetwork(stops, listOf(a, b), listOf(Place(1, "Hospital Central", "hospital", p(-15.601, -56.099))), null)
}

class NetworkTest {
    @Test fun pontoMaisProximo() {
        val near = FakeNet.network.nearestStops(GeoPoint(-15.6005, -56.0995), limit = 2)
        assertEquals("A1", near.first().stop.name)
        assertTrue(near.first().distanceMeters < 100)
    }

    @Test fun linhasNoPonto() {
        val refs = FakeNet.network.patternsAtStop(3).map { it.ref }
        assertEquals(listOf("101", "202"), refs)
    }

    @Test fun buscaOffline() {
        val hits = FakeNet.network.searchPlaces("hosp")
        assertEquals("Hospital Central", hits.first().title)
        assertEquals(listOf("101"), FakeNet.network.searchLines("10").map { it.ref })
        assertEquals(listOf("202"), FakeNet.network.searchLines("norte").map { it.ref })
    }
}

class RouterTest {
    private val router = Router(FakeNet.network)

    @Test fun linhaDireta() {
        val options = router.route(GeoPoint(-15.6003, -56.1003), "Origem", GeoPoint(-15.6003, -56.0403), "Destino")
        val best = options.first { !it.isWalkOnly }
        assertEquals(0, best.transfers)
        assertEquals("101", best.rides.single().pattern.ref)
        assertEquals("A1", best.rides.single().board.name)
        assertEquals("A4", best.rides.single().alight.name)
        assertTrue(best.legs.first() is Leg.Walk)
        assertTrue(best.legs.last() is Leg.Walk)
    }

    @Test fun umaBaldeacao() {
        val options = router.route(GeoPoint(-15.6003, -56.1003), "Origem", GeoPoint(-15.5603, -56.0603), "Destino")
        val best = options.first()
        assertEquals(1, best.transfers)
        assertEquals(listOf("101", "202"), best.rides.map { it.pattern.ref })
        assertEquals("Cruzamento", best.rides[0].alight.name)
        assertEquals("Cruzamento", best.rides[1].board.name)
    }

    @Test fun semSentidoContrario() {
        // A linha 101 só vai para leste: de A4 para A1 não há ônibus.
        val options = router.route(GeoPoint(-15.6003, -56.0403), "Origem", GeoPoint(-15.6003, -56.1003), "Destino")
        assertTrue(options.none { !it.isWalkOnly })
    }

    @Test fun sugereCaminharQuandoPerto() {
        val options = router.route(GeoPoint(-15.600, -56.100), "Origem", GeoPoint(-15.600, -56.095), "Destino")
        assertTrue(options.any { it.isWalkOnly })
    }

    @Test fun redeVaziaNaoQuebra() {
        val options = Router(TransitNetwork.EMPTY).route(GeoPoint(0.0, 0.0), "o", GeoPoint(1.0, 1.0), "d")
        assertTrue(options.isEmpty())
    }
}

class ConsentTest {
    private fun record(action: ConsentAction, terms: String = "1.0.0", privacy: String = "1.0.0") = ConsentRecord(
        "id", action, terms, privacy, "h1", "h2", emptyMap(), "2026-11-07T15:42:10-04:00", "2026-11-07T19:42:10Z", "1.0.0", "1",
    )

    @Test fun status() {
        assertEquals(ConsentStatus.PENDENTE, ConsentRules.status(null, "1.0.0", "1.0.0"))
        assertEquals(ConsentStatus.ACEITO, ConsentRules.status(record(ConsentAction.ACEITO), "1.0.0", "1.0.0"))
        assertEquals(ConsentStatus.RECUSADO, ConsentRules.status(record(ConsentAction.RECUSADO), "1.0.0", "1.0.0"))
        assertEquals(ConsentStatus.PENDENTE, ConsentRules.status(record(ConsentAction.REVOGADO), "1.0.0", "1.0.0"))
        assertEquals(ConsentStatus.NOVA_VERSAO, ConsentRules.status(record(ConsentAction.ACEITO), "1.1.0", "1.0.0"))
        assertEquals(ConsentStatus.ACEITO, ConsentRules.status(record(ConsentAction.ATUALIZADO), "1.0.0", "1.0.0"))
    }

    @Test fun horarioDeCuiaba() {
        val (local, utc) = ConsentClock.timestamps(Instant.parse("2026-11-07T19:42:10.789Z"))
        assertEquals("2026-11-07T15:42:10-04:00", local)
        assertEquals("2026-11-07T19:42:10Z", utc)
        assertEquals("07/11/2026 às 15:42:10 (horário de Cuiabá)", ConsentClock.display(local))
    }

    @Test fun hash() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256Hex("abc"),
        )
    }
}
