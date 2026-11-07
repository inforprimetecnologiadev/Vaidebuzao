package br.com.inforprimetecnologia.vaidebuzao.core

import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Polyline
import br.com.inforprimetecnologia.vaidebuzao.core.recording.RecordedLine
import br.com.inforprimetecnologia.vaidebuzao.core.recording.Recording
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Router
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTest {
    // Gravação que passa pelo ponto "A1" da rede fictícia (FakeNet) e termina num lugar sem ponto.
    private val trace = (0..40).map { GeoPoint(-15.6003, -56.1003 + it * 0.0005) } // ~2,1 km para leste
    private val line = RecordedLine(
        id = 7, number = "999", name = "999 Teste", from = "Centro", to = "Bairro Novo", operator = null,
        trace = trace,
        stops = listOf(GeoPoint(-15.6001, -56.1001), GeoPoint(-15.6003, -56.0900), GeoPoint(-15.6003, -56.0803)),
        date = "2026-11-07",
    )

    @Test fun reduzTrajeto() {
        val thin = Recording.thin(trace, minMeters = 100.0)
        assertTrue(thin.size < trace.size)
        assertEquals(trace.first(), thin.first())
        assertEquals(trace.last(), thin.last())
    }

    @Test fun simplificaRetaParaDoisPontos() {
        val reta = (0..200).map { GeoPoint(-15.6, -56.1 + it * 0.0001) }
        assertEquals(2, Recording.simplify(reta).size)
        val curva = reta + (1..100).map { GeoPoint(-15.6 + it * 0.0001, -56.08) }
        assertEquals(3, Recording.simplify(curva).size)
    }

    @Test fun mesclaNaRedeEGrudaEmPontoExistente() {
        val merged = Recording.merge(FakeNet.network, listOf(line))
        val p = merged.patterns.single { it.ref == "999" }
        assertTrue(p.isUserRecorded)
        assertEquals("A1", merged.stopById(p.stopIds[0])?.name) // grudou no ponto da base
        assertEquals("A2", merged.stopById(p.stopIds[1])?.name)
        assertTrue(p.stopIds[2] < 0) // ponto novo marcado pelo usuário
        assertEquals(FakeNet.network.stops.size + 1, merged.stops.size)
    }

    @Test fun linhaGravadaEntraNasRotas() {
        val merged = Recording.merge(FakeNet.network, listOf(line))
        val options = Router(merged).route(GeoPoint(-15.6003, -56.1003), "o", GeoPoint(-15.6003, -56.0800), "d")
        assertTrue(options.any { it.rides.any { r -> r.pattern.ref == "999" } })
    }

    @Test fun exportaNoFormatoDoProjeto() {
        val json = Recording.toProjectJson(line, "1.0.0")
        assertTrue(json.contains("\"numero\": \"999\""))
        assertTrue(json.contains("\"para\": \"Bairro Novo\""))
        assertTrue(json.contains("\"lat\": -15.600100"))
        val poly = Regex("\"trajeto_polyline\": \"(.*)\"").find(json)!!.groupValues[1]
        assertTrue(Polyline.decode(poly.replace("\\\\", "\\")).size >= 2)
        assertEquals("linha-999-para-bairro-novo-7.json", Recording.fileName(line))
    }

    @Test fun ignoraGravacaoComMenosDeDoisPontos() {
        val merged = Recording.merge(FakeNet.network, listOf(line.copy(stops = listOf(GeoPoint(-15.6, -56.1)))))
        assertEquals(FakeNet.network.patterns.size, merged.patterns.size)
    }
}
