package br.com.inforprimetecnologia.vaidebuzao.data

import br.com.inforprimetecnologia.vaidebuzao.BuildConfig
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RemotePlace(val title: String, val subtitle: String, val point: GeoPoint)

/**
 * Busca de endereços no Nominatim (OpenStreetMap), gratuito e sem chave.
 * Respeita a política de uso: no máximo 1 requisição/segundo, User-Agent identificado, cache e recorte da região.
 * Só é chamado quando a busca offline não basta. O texto digitado é enviado ao serviço (ver Política de Privacidade).
 */
class GeocodingRepository {
    private val mutex = Mutex()
    private var lastRequestAt = 0L
    private val cache = object : LinkedHashMap<String, List<RemotePlace>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RemotePlace>>?) = size > 40
    }

    suspend fun search(query: String): Result<List<RemotePlace>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 3) return@withContext Result.success(emptyList())
        mutex.withLock {
            cache[q.lowercase()]?.let { return@withContext Result.success(it) }
            val wait = 1100 - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
            runCatching {
                val url = URL(
                    "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&countrycodes=br" +
                        "&bounded=1&viewbox=$VIEWBOX&accept-language=pt-BR&q=" + URLEncoder.encode(q, "UTF-8"),
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "VaiDeBuzao/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.REPO_URL})")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    Json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                        val o = el.jsonObject
                        val lat = o["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = o["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val display = o["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val parts = display.split(", ")
                        val name = o["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: parts.first()
                        RemotePlace(name, parts.drop(1).take(3).joinToString(", "), GeoPoint(lat, lon))
                    }.also { cache[q.lowercase()] = it }
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private companion object {
        // oeste,norte,leste,sul — Região Metropolitana do Vale do Rio Cuiabá
        const val VIEWBOX = "-56.55,-15.10,-55.55,-16.05"
    }
}
