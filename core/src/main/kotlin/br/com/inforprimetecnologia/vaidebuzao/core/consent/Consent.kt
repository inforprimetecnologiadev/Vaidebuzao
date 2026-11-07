package br.com.inforprimetecnologia.vaidebuzao.core.consent

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Ações registradas no histórico de consentimento (LGPD art. 8º). */
enum class ConsentAction(val label: String) {
    ACEITO("Aceite"),
    RECUSADO("Recusa"),
    REVOGADO("Revogação"),
    ATUALIZADO("Preferências alteradas"),
    DADOS_APAGADOS("Dados apagados"),
}

/** Registro imutável de uma decisão do titular. */
data class ConsentRecord(
    val id: String,
    val action: ConsentAction,
    val termsVersion: String,
    val privacyVersion: String,
    val termsSha256: String,
    val privacySha256: String,
    /** Consentimentos opcionais (ex.: "historico_buscas" -> true). */
    val optional: Map<String, Boolean>,
    /** ISO-8601 com offset, no fuso America/Cuiaba. Ex.: 2026-11-07T15:42:10-04:00 */
    val localDateTime: String,
    /** ISO-8601 em UTC. Ex.: 2026-11-07T19:42:10Z */
    val utcDateTime: String,
    val appVersion: String,
    val dataVersion: String,
)

enum class ConsentStatus { PENDENTE, ACEITO, RECUSADO, NOVA_VERSAO }

object ConsentRules {
    /** Situação atual a partir do último registro e das versões vigentes dos documentos. */
    fun status(last: ConsentRecord?, termsVersion: String, privacyVersion: String): ConsentStatus = when {
        last == null -> ConsentStatus.PENDENTE
        last.action == ConsentAction.RECUSADO -> ConsentStatus.RECUSADO
        last.action == ConsentAction.REVOGADO || last.action == ConsentAction.DADOS_APAGADOS -> ConsentStatus.PENDENTE
        last.termsVersion != termsVersion || last.privacyVersion != privacyVersion -> ConsentStatus.NOVA_VERSAO
        else -> ConsentStatus.ACEITO
    }
}

object ConsentClock {
    val ZONE_CUIABA: ZoneId = ZoneId.of("America/Cuiaba")
    private val DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm:ss")

    /** Par (data/hora local de Cuiabá com offset, data/hora UTC), ambos ISO-8601 sem frações de segundo. */
    fun timestamps(instant: Instant = Instant.now()): Pair<String, String> {
        val t = instant.truncatedTo(ChronoUnit.SECONDS)
        val local = t.atZone(ZONE_CUIABA).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val utc = t.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
        return local to utc
    }

    /** "07/11/2026 às 15:42:10 (horário de Cuiabá)" */
    fun display(localIso: String): String = runCatching {
        java.time.OffsetDateTime.parse(localIso).atZoneSameInstant(ZONE_CUIABA).format(DISPLAY) + " (horário de Cuiabá)"
    }.getOrDefault(localIso)
}

object Hashing {
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
