package br.com.inforprimetecnologia.vaidebuzao.data

import android.content.Context
import br.com.inforprimetecnologia.vaidebuzao.core.consent.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class LegalDocType(val asset: String, val title: String) {
    TERMOS("legal/termos.md", "Termos de Uso"),
    PRIVACIDADE("legal/privacidade.md", "Política de Privacidade"),
}

data class LegalDoc(val type: LegalDocType, val version: String, val markdown: String, val sha256: String)

data class LegalBundle(
    val terms: LegalDoc,
    val privacy: LegalDoc,
    val config: Map<String, String>,
) {
    val controller: String get() = config["controlador"] ?: ""
    val privacyEmail: String get() = config["email_privacidade"] ?: config["email_contato"] ?: ""
    val contactEmail: String get() = config["email_contato"] ?: ""
    val changes: String get() = config["novidades"] ?: ""
}

/**
 * Textos legais versionados em assets/legal/. Os dados do controlador vêm de UM arquivo: assets/legal/config.json.
 * O hash SHA-256 é calculado sobre o texto final exibido ao usuário (com os dados preenchidos).
 */
class LegalRepository(private val context: Context) {
    @Volatile private var cached: LegalBundle? = null

    suspend fun bundle(): LegalBundle = cached ?: withContext(Dispatchers.IO) {
        val configText = context.assets.open("legal/config.json").bufferedReader().use { it.readText() }
        val config = Json.parseToJsonElement(configText).jsonObject.mapValues { it.value.jsonPrimitive.content }
        fun doc(type: LegalDocType, versionKey: String): LegalDoc {
            var text = context.assets.open(type.asset).bufferedReader().use { it.readText() }
            config.forEach { (k, v) -> text = text.replace("{{$k}}", v) }
            return LegalDoc(type, config[versionKey] ?: "1.0.0", text, Hashing.sha256Hex(text))
        }
        LegalBundle(doc(LegalDocType.TERMOS, "versao_termos"), doc(LegalDocType.PRIVACIDADE, "versao_privacidade"), config)
    }.also { cached = it }
}
