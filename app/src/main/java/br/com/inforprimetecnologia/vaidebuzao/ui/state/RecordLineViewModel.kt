package br.com.inforprimetecnologia.vaidebuzao.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.inforprimetecnologia.vaidebuzao.BuildConfig
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.recording.RecordedLine
import br.com.inforprimetecnologia.vaidebuzao.core.recording.Recording
import br.com.inforprimetecnologia.vaidebuzao.data.AppContainer
import br.com.inforprimetecnologia.vaidebuzao.data.LocationFix
import br.com.inforprimetecnologia.vaidebuzao.data.SavedRecording
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecPhase { SETUP, RECORDING, REVIEW }

data class RecState(
    val phase: RecPhase = RecPhase.SETUP,
    val id: Long = 0,
    val number: String = "",
    val name: String = "",
    val from: String = "",
    val to: String = "",
    val operator: String = "",
    val trace: List<GeoPoint> = emptyList(),
    val stops: List<GeoPoint> = emptyList(),
    /** Nome do ponto da base perto de cada ponto marcado (null = ponto novo). */
    val stopLabels: List<String?> = emptyList(),
    val lastFix: LocationFix? = null,
    val startedAt: Long = 0,
    val draft: SavedRecording? = null,
) {
    val canStart: Boolean get() = number.isNotBlank() && to.isNotBlank()
    val distanceMeters: Double get() = trace.zipWithNext().sumOf { (a, b) -> Geo.distanceMeters(a, b) }

    fun toLine(): RecordedLine = RecordedLine(
        id = id,
        number = number.trim(),
        name = name.trim().ifBlank { null },
        from = from.trim().ifBlank { null },
        to = to.trim(),
        operator = operator.trim().ifBlank { null },
        trace = trace,
        stops = stops,
        date = LocalDate.now(br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentClock.ZONE_CUIABA).toString(),
    )
}

/** Resultado de tocar em "Parou num ponto". */
sealed interface MarkResult {
    data class Ok(val index: Int, val nearStop: String?) : MarkResult
    data object NoGps : MarkResult
}

class RecordLineViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state.asStateFlow()

    val recordings: StateFlow<List<SavedRecording>> = c.recordings.all

    private var fixesSinceSave = 0

    init {
        viewModelScope.launch {
            c.recordings.load()
            _state.value = _state.value.copy(draft = c.recordings.draft())
        }
    }

    fun hasLocationPermission() = c.location.hasPermission()

    fun locationUpdates(): Flow<LocationFix> = c.location.updates()

    fun update(transform: (RecState) -> RecState) {
        _state.value = transform(_state.value)
    }

    fun start() {
        val s = _state.value
        if (!s.canStart) return
        _state.value = s.copy(phase = RecPhase.RECORDING, startedAt = System.currentTimeMillis(), draft = null)
        saveDraft()
    }

    fun resumeDraft() {
        val d = _state.value.draft ?: return
        val l = d.line
        _state.value = RecState(
            phase = RecPhase.RECORDING,
            id = l.id,
            number = l.number,
            name = l.name.orEmpty(),
            from = l.from.orEmpty(),
            to = l.to,
            operator = l.operator.orEmpty(),
            trace = l.trace,
            stops = l.stops,
            stopLabels = l.stops.map { null },
            startedAt = System.currentTimeMillis(),
        )
    }

    fun discardDraft() = viewModelScope.launch {
        _state.value.draft?.let { c.recordings.delete(it.line.id) }
        _state.value = _state.value.copy(draft = null)
    }

    fun onFix(fix: LocationFix) {
        val s = _state.value
        if (s.phase != RecPhase.RECORDING) return
        if (fix.accuracyMeters > Recording.MAX_ACCURACY_METERS) {
            _state.value = s.copy(lastFix = fix)
            return
        }
        val last = s.trace.lastOrNull()
        val trace = if (last == null || Geo.distanceMeters(last, fix.point) >= 5.0) s.trace + fix.point else s.trace
        _state.value = s.copy(lastFix = fix, trace = trace)
        if (++fixesSinceSave >= 20) saveDraft()
    }

    /** Marca um ponto de parada na posição atual do GPS. */
    fun markStop(): MarkResult {
        val s = _state.value
        val fix = s.lastFix
        val fresh = fix != null && System.currentTimeMillis() - fix.timeMillis < 20_000 &&
            fix.accuracyMeters <= Recording.MAX_ACCURACY_METERS
        if (!fresh) return MarkResult.NoGps
        val p = fix!!.point
        val index = s.stops.size
        viewModelScope.launch {
            val near = Recording.snap(c.transit.network(), p)?.displayName
            val cur = _state.value
            if (index < cur.stopLabels.size) {
                _state.value = cur.copy(stopLabels = cur.stopLabels.toMutableList().also { it[index] = near })
            }
        }
        _state.value = s.copy(stops = s.stops + p, stopLabels = s.stopLabels + null, trace = if (s.trace.lastOrNull() != p) s.trace + p else s.trace)
        saveDraft()
        return MarkResult.Ok(s.stops.size + 1, null)
    }

    fun undoStop() {
        val s = _state.value
        if (s.stops.isEmpty()) return
        _state.value = s.copy(stops = s.stops.dropLast(1), stopLabels = s.stopLabels.dropLast(1))
        saveDraft()
    }

    /** Salva como finalizada. Retorna false se faltar ponto. */
    fun finish(): Boolean {
        val s = _state.value
        if (s.stops.size < 2) return false
        viewModelScope.launch {
            val id = c.recordings.save(s.toLine(), finished = true)
            _state.value = _state.value.copy(id = id, phase = RecPhase.REVIEW)
        }
        return true
    }

    /** Pausa e guarda como rascunho (para continuar depois). */
    fun pauseAsDraft() = saveDraft()

    /** Prepara a gravação do sentido contrário da mesma linha. */
    fun recordReturn() {
        val s = _state.value
        _state.value = RecState(
            number = s.number,
            name = s.name,
            operator = s.operator,
            from = s.to,
            to = s.from,
        )
    }

    private fun saveDraft() {
        fixesSinceSave = 0
        val s = _state.value
        if (s.phase != RecPhase.RECORDING) return
        viewModelScope.launch {
            val id = c.recordings.save(s.toLine(), finished = false)
            if (_state.value.id == 0L) _state.value = _state.value.copy(id = id)
        }
    }

    fun delete(id: Long) = viewModelScope.launch { c.recordings.delete(id) }

    fun exportJson(line: RecordedLine): String = Recording.toProjectJson(line, BuildConfig.VERSION_NAME)

    /**
     * Link que abre o GitHub já com o arquivo pronto em data/linhas/ (basta tocar em "Commit changes").
     * null quando o arquivo é grande demais para caber num link.
     */
    fun githubNewFileUrl(line: RecordedLine): String? {
        val json = exportJson(line)
        val url = BuildConfig.REPO_URL + "/new/" + BuildConfig.DATA_BRANCH +
            "?filename=" + URLEncoder.encode("data/linhas/" + Recording.fileName(line), "UTF-8") +
            "&message=" + URLEncoder.encode("dados: linha ${line.number} sentido ${line.to} (gravada no app)", "UTF-8") +
            "&value=" + URLEncoder.encode(json, "UTF-8")
        return url.takeIf { it.length <= 7500 }
    }
}
