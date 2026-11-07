package br.com.inforprimetecnologia.vaidebuzao.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.inforprimetecnologia.vaidebuzao.BuildConfig
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentAction
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentRecord
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentRules
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentStatus
import br.com.inforprimetecnologia.vaidebuzao.data.AppContainer
import br.com.inforprimetecnologia.vaidebuzao.data.AppSettings
import br.com.inforprimetecnologia.vaidebuzao.data.LegalBundle
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement

/** Em que etapa o app está: o conteúdo só aparece depois do aceite (LGPD). */
sealed interface Gate {
    data object Loading : Gate
    data class NeedsConsent(val isUpdate: Boolean) : Gate
    data object Declined : Gate
    data object Onboarding : Gate
    data object Ready : Gate
}

const val OPT_HISTORY = "historico_buscas"

class AppViewModel(private val c: AppContainer) : ViewModel() {

    private val _legal = MutableStateFlow<LegalBundle?>(null)
    val legal: StateFlow<LegalBundle?> = _legal.asStateFlow()

    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val consentRecords: StateFlow<List<ConsentRecord>?> = c.consent.records

    /** Permite ver o app de novo depois de "Recusar" → "Rever termos". */
    private val reviewing = MutableStateFlow(false)

    val gate: StateFlow<Gate> = combine(_legal, settings, consentRecords, reviewing) { legal, s, records, review ->
        if (legal == null || s == null || records == null) return@combine Gate.Loading
        when (ConsentRules.status(records.firstOrNull(), legal.terms.version, legal.privacy.version)) {
            ConsentStatus.PENDENTE -> Gate.NeedsConsent(isUpdate = false)
            ConsentStatus.NOVA_VERSAO -> Gate.NeedsConsent(isUpdate = true)
            ConsentStatus.RECUSADO -> if (review) Gate.NeedsConsent(false) else Gate.Declined
            ConsentStatus.ACEITO -> if (s.onboardingDone) Gate.Ready else Gate.Onboarding
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.Loading)

    init {
        viewModelScope.launch {
            _legal.value = c.legal.bundle()
            c.consent.load()
            c.favorites.load()
        }
    }

    private suspend fun dataVersion(): String = c.transit.network().meta?.dataVersion ?: "sem-dados"

    private suspend fun record(action: ConsentAction, optional: Map<String, Boolean>) {
        val legal = _legal.value ?: c.legal.bundle()
        c.consent.append(
            action = action,
            termsVersion = legal.terms.version,
            privacyVersion = legal.privacy.version,
            termsHash = legal.terms.sha256,
            privacyHash = legal.privacy.sha256,
            optional = optional,
            appVersion = BuildConfig.VERSION_NAME,
            dataVersion = dataVersion(),
        )
    }

    fun accept(historyEnabled: Boolean) = viewModelScope.launch {
        c.settings.setHistoryEnabled(historyEnabled)
        reviewing.value = false
        record(ConsentAction.ACEITO, mapOf(OPT_HISTORY to historyEnabled))
    }

    fun decline() = viewModelScope.launch {
        reviewing.value = false
        record(ConsentAction.RECUSADO, emptyMap())
    }

    fun reviewTermsAgain() {
        reviewing.value = true
    }

    fun revoke() = viewModelScope.launch {
        c.settings.setHistoryEnabled(false)
        c.favorites.clearHistory()
        record(ConsentAction.REVOGADO, mapOf(OPT_HISTORY to false))
    }

    fun setHistoryEnabled(enabled: Boolean) = viewModelScope.launch {
        c.settings.setHistoryEnabled(enabled)
        if (!enabled) c.favorites.clearHistory()
        record(ConsentAction.ATUALIZADO, mapOf(OPT_HISTORY to enabled))
    }

    /** "Apagar meus dados": tudo some; resta só o registro mínimo da exclusão. */
    fun wipeAll() = viewModelScope.launch {
        c.favorites.wipe()
        c.settings.clear()
        c.consent.wipe()
        record(ConsentAction.DADOS_APAGADOS, emptyMap())
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }

    fun finishOnboarding() = viewModelScope.launch { c.settings.setOnboardingDone(true) }

    /** JSON com tudo o que o app guarda sobre o usuário (direito de acesso e portabilidade, art. 18). */
    suspend fun exportJson(): String {
        val s = c.settings.settings.first()
        val favs = c.favorites.favorites.value
        val hist = c.favorites.history.value
        val records = consentRecords.value.orEmpty()
        fun str(v: String?): JsonElement = JsonPrimitive(v)
        val root = JsonObject(
            mapOf(
                "app" to str("Vai de Buzão"),
                "versao_app" to str(BuildConfig.VERSION_NAME),
                "gerado_em" to str(br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentClock.timestamps().first),
                "observacao" to str("Estes são todos os dados pessoais que o app guarda, somente neste aparelho."),
                "preferencias" to JsonObject(
                    mapOf(
                        "tema" to str(s.themeMode.name),
                        "onboarding_concluido" to JsonPrimitive(s.onboardingDone),
                        "historico_buscas_ativado" to JsonPrimitive(s.historyEnabled),
                    ),
                ),
                "favoritos" to JsonArray(
                    favs.map {
                        JsonObject(
                            mapOf(
                                "tipo" to str(it.type.name), "ref" to str(it.ref), "titulo" to str(it.title),
                                "lat" to JsonPrimitive(it.lat), "lon" to JsonPrimitive(it.lon),
                            ),
                        )
                    },
                ),
                "historico_buscas" to JsonArray(
                    hist.map {
                        JsonObject(
                            mapOf(
                                "titulo" to str(it.title), "lat" to JsonPrimitive(it.lat), "lon" to JsonPrimitive(it.lon),
                                "data_hora" to str(it.createdAt),
                            ),
                        )
                    },
                ),
                "registro_consentimento" to JsonArray(
                    records.map { r ->
                        JsonObject(
                            mapOf(
                                "id" to str(r.id),
                                "acao" to str(r.action.name),
                                "versao_termos" to str(r.termsVersion),
                                "versao_privacidade" to str(r.privacyVersion),
                                "hash_sha256_termos" to str(r.termsSha256),
                                "hash_sha256_privacidade" to str(r.privacySha256),
                                "consentimentos_opcionais" to JsonObject(r.optional.mapValues { JsonPrimitive(it.value) }),
                                "data_hora_local" to str(r.localDateTime),
                                "data_hora_utc" to str(r.utcDateTime),
                                "versao_app" to str(r.appVersion),
                                "versao_dados" to str(r.dataVersion),
                            ),
                        )
                    },
                ),
            ),
        )
        return kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
    }
}
