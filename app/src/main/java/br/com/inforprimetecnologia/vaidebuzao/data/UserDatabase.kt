package br.com.inforprimetecnologia.vaidebuzao.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentAction
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentClock
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Banco local do usuário (somente no aparelho): histórico de consentimento, favoritos e histórico de buscas.
 * A tabela consent_log é "somente inserção": nunca atualizamos linhas, apenas acrescentamos.
 */
class UserDatabase(context: Context) : SQLiteOpenHelper(context, "usuario.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE consent_log(
                id TEXT PRIMARY KEY,
                acao TEXT NOT NULL,
                versao_termos TEXT NOT NULL,
                versao_privacidade TEXT NOT NULL,
                hash_sha256_termos TEXT NOT NULL,
                hash_sha256_privacidade TEXT NOT NULL,
                consentimentos_opcionais TEXT NOT NULL,
                data_hora_local TEXT NOT NULL,
                data_hora_utc TEXT NOT NULL,
                versao_app TEXT NOT NULL,
                versao_dados TEXT NOT NULL,
                seq INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE favoritos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL,
                ref TEXT NOT NULL,
                titulo TEXT NOT NULL,
                lat REAL, lon REAL,
                criado_em TEXT NOT NULL,
                UNIQUE(tipo, ref)
            )""",
        )
        db.execSQL(
            """CREATE TABLE historico(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL,
                criado_em TEXT NOT NULL
            )""",
        )
        createRecordings(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createRecordings(db)
    }

    /** v2: linhas gravadas pelo usuário ("Gravar linha"). Ficam só no aparelho. */
    private fun createRecordings(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS gravacoes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                numero TEXT NOT NULL,
                nome TEXT,
                de TEXT,
                para TEXT NOT NULL,
                operadora TEXT,
                trajeto TEXT NOT NULL,
                pontos TEXT NOT NULL,
                data TEXT NOT NULL,
                finalizada INTEGER NOT NULL DEFAULT 0
            )""",
        )
    }
}

enum class FavoriteType { PONTO, LINHA, LUGAR }

data class Favorite(val id: Long, val type: FavoriteType, val ref: String, val title: String, val lat: Double?, val lon: Double?)

data class HistoryItem(val id: Long, val title: String, val lat: Double, val lon: Double, val createdAt: String)

class ConsentRepository(private val db: UserDatabase) {
    private val _records = MutableStateFlow<List<ConsentRecord>?>(null)

    /** Histórico completo (mais recente primeiro); null enquanto carrega. */
    val records: StateFlow<List<ConsentRecord>?> = _records.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val out = ArrayList<ConsentRecord>()
        db.readableDatabase.rawQuery("SELECT * FROM consent_log ORDER BY seq DESC", null).use { c ->
            while (c.moveToNext()) {
                fun s(col: String) = c.getString(c.getColumnIndexOrThrow(col))
                val optional = runCatching {
                    Json.parseToJsonElement(s("consentimentos_opcionais")).jsonObject.mapValues { it.value.jsonPrimitive.boolean }
                }.getOrDefault(emptyMap())
                out += ConsentRecord(
                    id = s("id"),
                    action = runCatching { ConsentAction.valueOf(s("acao")) }.getOrDefault(ConsentAction.REVOGADO),
                    termsVersion = s("versao_termos"),
                    privacyVersion = s("versao_privacidade"),
                    termsSha256 = s("hash_sha256_termos"),
                    privacySha256 = s("hash_sha256_privacidade"),
                    optional = optional,
                    localDateTime = s("data_hora_local"),
                    utcDateTime = s("data_hora_utc"),
                    appVersion = s("versao_app"),
                    dataVersion = s("versao_dados"),
                )
            }
        }
        _records.value = out
    }

    suspend fun append(
        action: ConsentAction,
        termsVersion: String,
        privacyVersion: String,
        termsHash: String,
        privacyHash: String,
        optional: Map<String, Boolean>,
        appVersion: String,
        dataVersion: String,
    ): ConsentRecord = withContext(Dispatchers.IO) {
        val (local, utc) = ConsentClock.timestamps()
        val record = ConsentRecord(
            id = java.util.UUID.randomUUID().toString(),
            action = action,
            termsVersion = termsVersion,
            privacyVersion = privacyVersion,
            termsSha256 = termsHash,
            privacySha256 = privacyHash,
            optional = optional,
            localDateTime = local,
            utcDateTime = utc,
            appVersion = appVersion,
            dataVersion = dataVersion,
        )
        val w = db.writableDatabase
        val seq = w.rawQuery("SELECT COALESCE(MAX(seq), 0) + 1 FROM consent_log", null).use { c -> c.moveToFirst(); c.getLong(0) }
        w.insertOrThrow(
            "consent_log",
            null,
            ContentValues().apply {
                put("id", record.id)
                put("acao", record.action.name)
                put("versao_termos", termsVersion)
                put("versao_privacidade", privacyVersion)
                put("hash_sha256_termos", termsHash)
                put("hash_sha256_privacidade", privacyHash)
                put("consentimentos_opcionais", JsonObject(optional.mapValues { JsonPrimitive(it.value) }).toString())
                put("data_hora_local", local)
                put("data_hora_utc", utc)
                put("versao_app", appVersion)
                put("versao_dados", dataVersion)
                put("seq", seq)
            },
        )
        load()
        record
    }

    /** "Apagar meus dados": remove o histórico. Em seguida o chamador registra DADOS_APAGADOS. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("consent_log", null, null)
        load()
    }
}

class FavoritesRepository(private val db: UserDatabase) {
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val favs = ArrayList<Favorite>()
        db.readableDatabase.rawQuery("SELECT id, tipo, ref, titulo, lat, lon FROM favoritos ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                val type = runCatching { FavoriteType.valueOf(c.getString(1)) }.getOrNull() ?: continue
                favs += Favorite(
                    c.getLong(0), type, c.getString(2), c.getString(3),
                    if (c.isNull(4)) null else c.getDouble(4),
                    if (c.isNull(5)) null else c.getDouble(5),
                )
            }
        }
        _favorites.value = favs
        val hist = ArrayList<HistoryItem>()
        db.readableDatabase.rawQuery("SELECT id, titulo, lat, lon, criado_em FROM historico ORDER BY id DESC LIMIT 30", null).use { c ->
            while (c.moveToNext()) hist += HistoryItem(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getString(4))
        }
        _history.value = hist
    }

    fun isFavorite(type: FavoriteType, ref: String): Boolean = _favorites.value.any { it.type == type && it.ref == ref }

    suspend fun toggle(type: FavoriteType, ref: String, title: String, lat: Double?, lon: Double?) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        val deleted = w.delete("favoritos", "tipo = ? AND ref = ?", arrayOf(type.name, ref))
        if (deleted == 0) {
            w.insert(
                "favoritos",
                null,
                ContentValues().apply {
                    put("tipo", type.name)
                    put("ref", ref)
                    put("titulo", title)
                    if (lat != null) put("lat", lat)
                    if (lon != null) put("lon", lon)
                    put("criado_em", ConsentClock.timestamps().first)
                },
            )
        }
        load()
    }

    suspend fun addHistory(title: String, lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.delete("historico", "titulo = ?", arrayOf(title))
        w.insert(
            "historico",
            null,
            ContentValues().apply {
                put("titulo", title)
                put("lat", lat)
                put("lon", lon)
                put("criado_em", ConsentClock.timestamps().first)
            },
        )
        w.execSQL("DELETE FROM historico WHERE id NOT IN (SELECT id FROM historico ORDER BY id DESC LIMIT 30)")
        load()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("historico", null, null)
        load()
    }

    suspend fun wipe() = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("historico", null, null)
        db.writableDatabase.delete("favoritos", null, null)
        load()
    }
}
