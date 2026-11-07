package br.com.inforprimetecnologia.vaidebuzao.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import br.com.inforprimetecnologia.vaidebuzao.BuildConfig
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Polyline
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Place
import br.com.inforprimetecnologia.vaidebuzao.core.transit.RoutePattern
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Stop
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitMeta
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Carrega a base de transporte embutida (assets/transit.db, gerada por tools/data) para a memória.
 * Funciona 100% offline. Se o arquivo não existir, devolve uma rede vazia (o app mostra estado vazio).
 */
class TransitRepository(private val context: Context) {
    private val mutex = Mutex()
    private var cached: TransitNetwork? = null

    suspend fun network(): TransitNetwork = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    private fun copyAssetIfNeeded(): File? {
        val target = File(context.noBackupFilesDir, "transit-${BuildConfig.VERSION_CODE}.db")
        if (target.exists() && target.length() > 0) return target
        context.noBackupFilesDir.listFiles { f -> f.name.startsWith("transit-") }?.forEach { it.delete() }
        return try {
            context.assets.open(ASSET).use { input ->
                val tmp = File(target.path + ".tmp")
                tmp.outputStream().use { input.copyTo(it) }
                tmp.renameTo(target)
            }
            target
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun load(): TransitNetwork {
        val file = copyAssetIfNeeded() ?: return TransitNetwork.EMPTY
        return runCatching {
            SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).use { db -> read(db) }
        }.getOrElse { TransitNetwork.EMPTY }
    }

    private fun read(db: SQLiteDatabase): TransitNetwork {
        val meta = HashMap<String, String>()
        db.rawQuery("SELECT key, value FROM meta", null).use { c -> while (c.moveToNext()) meta[c.getString(0)] = c.getString(1) }

        val stops = ArrayList<Stop>()
        db.rawQuery("SELECT id, name, lat, lon, area FROM stops", null).use { c ->
            while (c.moveToNext()) {
                stops += Stop(
                    id = c.getInt(0),
                    name = if (c.isNull(1)) null else c.getString(1),
                    point = GeoPoint(c.getDouble(2), c.getDouble(3)),
                    area = if (c.isNull(4)) null else c.getString(4),
                )
            }
        }

        val stopsByPattern = HashMap<Int, ArrayList<Int>>()
        db.rawQuery("SELECT pattern_id, stop_id FROM pattern_stops ORDER BY pattern_id, seq", null).use { c ->
            while (c.moveToNext()) stopsByPattern.getOrPut(c.getInt(0)) { ArrayList() }.add(c.getInt(1))
        }

        val patterns = ArrayList<RoutePattern>()
        db.rawQuery(
            "SELECT id, ref, name, from_name, to_name, color, operator, headway_min, inferred, shape FROM patterns",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getInt(0)
                patterns += RoutePattern(
                    id = id,
                    ref = c.getString(1) ?: "",
                    name = c.getString(2) ?: "",
                    from = if (c.isNull(3)) null else c.getString(3),
                    to = if (c.isNull(4)) null else c.getString(4),
                    colorHex = if (c.isNull(5)) null else c.getString(5),
                    operator = if (c.isNull(6)) null else c.getString(6),
                    headwayMinutes = if (c.isNull(7)) null else c.getInt(7),
                    stopIds = stopsByPattern[id] ?: emptyList(),
                    shape = if (c.isNull(9)) emptyList() else Polyline.decode(c.getString(9)),
                    inferredStops = c.getInt(8) == 1,
                )
            }
        }

        val places = ArrayList<Place>()
        db.rawQuery("SELECT id, name, kind, lat, lon FROM places", null).use { c ->
            while (c.moveToNext()) places += Place(c.getInt(0), c.getString(1), c.getString(2), GeoPoint(c.getDouble(3), c.getDouble(4)))
        }

        return TransitNetwork(
            stops = stops,
            patterns = patterns,
            places = places,
            meta = TransitMeta(
                dataVersion = meta["data_version"] ?: "?",
                generatedAt = meta["generated_at"] ?: "?",
                source = meta["source"] ?: "OpenStreetMap",
            ),
        )
    }

    private companion object {
        const val ASSET = "transit.db"
    }
}
