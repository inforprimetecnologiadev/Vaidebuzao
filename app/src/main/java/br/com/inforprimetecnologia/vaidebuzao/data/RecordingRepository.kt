package br.com.inforprimetecnologia.vaidebuzao.data

import android.content.ContentValues
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Polyline
import br.com.inforprimetecnologia.vaidebuzao.core.recording.RecordedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Gravação salva no aparelho. [finished] = false é um rascunho (gravação interrompida). */
data class SavedRecording(val line: RecordedLine, val finished: Boolean)

/**
 * Linhas gravadas pelo usuário. Ficam SOMENTE no aparelho; só saem se o usuário compartilhar.
 * Rascunhos são salvos durante a gravação, para não perder a viagem se o app fechar.
 */
class RecordingRepository(private val db: UserDatabase) {
    private val _all = MutableStateFlow<List<SavedRecording>>(emptyList())
    val all: StateFlow<List<SavedRecording>> = _all.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val out = ArrayList<SavedRecording>()
        db.readableDatabase.rawQuery(
            "SELECT id, numero, nome, de, para, operadora, trajeto, pontos, data, finalizada FROM gravacoes ORDER BY id DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += SavedRecording(
                    RecordedLine(
                        id = c.getLong(0),
                        number = c.getString(1),
                        name = c.getString(2),
                        from = c.getString(3),
                        to = c.getString(4),
                        operator = c.getString(5),
                        trace = Polyline.decode(c.getString(6)),
                        stops = Polyline.decode(c.getString(7)),
                        date = c.getString(8),
                    ),
                    finished = c.getInt(9) == 1,
                )
            }
        }
        _all.value = out
    }

    /** Cria ou atualiza; devolve o id. */
    suspend fun save(line: RecordedLine, finished: Boolean): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("numero", line.number.trim())
            put("nome", line.name?.trim())
            put("de", line.from?.trim())
            put("para", line.to.trim())
            put("operadora", line.operator?.trim())
            put("trajeto", Polyline.encode(line.trace))
            put("pontos", Polyline.encode(line.stops))
            put("data", line.date)
            put("finalizada", if (finished) 1 else 0)
        }
        val w = db.writableDatabase
        val id = if (line.id > 0 && w.update("gravacoes", values, "id = ?", arrayOf(line.id.toString())) > 0) {
            line.id
        } else {
            w.insertOrThrow("gravacoes", null, values)
        }
        load()
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("gravacoes", "id = ?", arrayOf(id.toString()))
        load()
    }

    suspend fun wipe() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("gravacoes", null, null)
        load()
    }

    fun draft(): SavedRecording? = _all.value.firstOrNull { !it.finished }

    fun finishedLines(): List<RecordedLine> = _all.value.filter { it.finished }.map { it.line }

    companion object {
        fun point(lat: Double, lon: Double) = GeoPoint(lat, lon)
    }
}
