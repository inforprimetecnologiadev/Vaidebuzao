package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.recording.RecordedLine
import br.com.inforprimetecnologia.vaidebuzao.core.recording.Recording
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoPill
import br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadge
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SectionHeader
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbAccentButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbCard
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbPrimaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbSecondaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.map.CameraRequest
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapMarker
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapPath
import br.com.inforprimetecnologia.vaidebuzao.ui.map.VdbMap
import br.com.inforprimetecnologia.vaidebuzao.ui.state.MarkResult
import br.com.inforprimetecnologia.vaidebuzao.ui.state.RecPhase
import br.com.inforprimetecnologia.vaidebuzao.ui.state.RecordLineViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SHARE_NOTE =
    "O arquivo tem o número e o destino da linha, os pontos que você marcou e o trajeto do ônibus, sem horário. " +
        "Ele mostra por onde você passou nessa viagem: só envie se estiver de acordo."

/** Compartilha o arquivo da linha (WhatsApp, e-mail, Drive... ou para o Claude Code). */
fun shareRecordedLine(context: Context, line: RecordedLine, json: String) {
    val dir = File(context.cacheDir, "exportacao").apply { mkdirs() }
    val file = File(dir, Recording.fileName(line)).apply { writeText(json) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Vai de Buzão – linha ${line.number} sentido ${line.to}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar linha gravada"))
}

@Composable
fun RecordLineScreen(vm: RecordLineViewModel, onConsent: () -> Unit, onBack: () -> Unit, onOpenRecordings: () -> Unit) {
    val state by vm.state.collectAsState()
    when (state.phase) {
        RecPhase.SETUP -> RecordSetup(vm, onConsent, onBack, onOpenRecordings)
        RecPhase.RECORDING -> RecordLive(vm, onBack)
        RecPhase.REVIEW -> RecordReview(vm, onBack)
    }
}

@Composable
private fun RecordSetup(vm: RecordLineViewModel, onConsent: () -> Unit, onBack: () -> Unit, onOpenRecordings: () -> Unit) {
    val state by vm.state.collectAsState()
    var hasPermission by remember { mutableStateOf(vm.hasLocationPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = vm.hasLocationPermission()
        if (hasPermission && state.canStart) {
            onConsent()
            vm.start()
        }
    }
    Scaffold(
        topBar = {
            VdbTopBar("Gravar linha", onBack = onBack, actions = { TextButton(onClick = onOpenRecordings) { Text("Minhas gravações") } })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.draft?.let { d ->
                VdbCard(Modifier.fillMaxWidth()) {
                    Text("Gravação interrompida", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Linha ${d.line.number} sentido ${d.line.to} · ${d.line.stops.size} pontos marcados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VdbPrimaryButton("Continuar", { vm.resumeDraft() }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                        VdbSecondaryButton("Descartar", { vm.discardDraft() }, Modifier.weight(1f))
                    }
                }
            }
            Text("Ajude a colocar as linhas no app", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Dentro do ônibus, o app grava o trajeto e você marca cada parada. No fim, a linha já passa a valer no " +
                    "seu celular e você pode enviá-la para o projeto, para entrar no app de todo mundo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            StepRow("1", "Antes de sair, preencha o número e o destino do letreiro.")
            StepRow("2", "Toque em \"Parou num ponto\" em cada parada do ônibus.")
            StepRow("3", "No fim, toque em \"Finalizar\". Grave a volta como outro sentido.")
            InfoBanner(
                "Durante a gravação o app usa sua localização a cada poucos segundos. O trajeto fica salvo só neste celular. " +
                    "Ele só sai daqui se você mesmo enviar. Você pode apagar quando quiser em Ajustes › Privacidade.",
                icon = Icons.Filled.Info,
            )
            SectionHeader("Dados da linha")
            OutlinedTextField(
                value = state.number,
                onValueChange = { v -> vm.update { it.copy(number = v.take(12)) } },
                label = { Text("Número da linha *") },
                placeholder = { Text("Ex.: 101, 024, 312A") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.to,
                onValueChange = { v -> vm.update { it.copy(to = v.take(60)) } },
                label = { Text("Destino do letreiro (para onde vai) *") },
                placeholder = { Text("Ex.: CPA III") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.from,
                onValueChange = { v -> vm.update { it.copy(from = v.take(60)) } },
                label = { Text("Saindo de (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> vm.update { it.copy(name = v.take(80)) } },
                label = { Text("Nome da linha (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.operator,
                onValueChange = { v -> vm.update { it.copy(operator = v.take(60)) } },
                label = { Text("Empresa (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Só grave o que você conferiu no ônibus. Não invente: um dado errado atrapalha outras pessoas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VdbAccentButton(
                if (hasPermission) "Começar gravação" else "Permitir localização e começar",
                {
                    if (!state.canStart) return@VdbAccentButton
                    if (hasPermission) {
                        onConsent()
                        vm.start()
                    } else {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
                Modifier.fillMaxWidth().alphaIf(!state.canStart),
                icon = Icons.Filled.FiberManualRecord,
            )
            if (!state.canStart) {
                Text("Preencha o número e o destino para começar.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Modifier.alphaIf(dim: Boolean): Modifier = if (dim) this.alpha(0.5f) else this

@Composable
private fun StepRow(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineBadge(n, "#FFC800")
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RecordLive(vm: RecordLineViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ext = VdbTheme.colors
    var confirmExit by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tela ligada durante a gravação
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // GPS só enquanto o app está na tela (regra de privacidade)
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.locationUpdates().collect { vm.onFix(it) }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    BackHandler { confirmExit = true }

    val fix = state.lastFix
    val gpsOk = fix != null && now - fix.timeMillis < 20_000 && fix.accuracyMeters <= Recording.MAX_ACCURACY_METERS
    val elapsedMin = ((now - state.startedAt) / 60_000.0).coerceAtLeast(0.0)
    val lineHex = ext.lineDefault.toHex()

    Scaffold(
        topBar = { VdbTopBar("Gravando linha ${state.number}", onBack = { confirmExit = true }) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            VdbMap(
                modifier = Modifier.fillMaxWidth().weight(1f),
                darkTheme = VdbTheme.isDark,
                userLocation = fix?.point,
                userColorHex = ext.userLocation.toHex(),
                paths = listOf(MapPath(state.trace, lineHex)),
                markers = state.stops.map { MapMarker(it, ext.stop.toHex()) },
                camera = fix?.let { CameraRequest((state.trace.size / 4) + 1, center = it.point, zoom = 16.5) },
            )
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sentido ${state.to}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoPill(
                        if (gpsOk) "GPS ok (${fix!!.accuracyMeters.toInt()} m)" else "Procurando GPS…",
                        if (gpsOk) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
                    )
                    InfoPill(Geo.formatMinutes(elapsedMin), Icons.Filled.Timer)
                    InfoPill(Geo.formatDistance(state.distanceMeters))
                }
                val lastLabel = state.stopLabels.lastOrNull()
                Text(
                    when {
                        state.stops.isEmpty() -> "Nenhum ponto marcado ainda."
                        lastLabel != null -> "${state.stops.size} pontos marcados · último perto de: $lastLabel"
                        else -> "${state.stops.size} pontos marcados"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        when (vm.markStop()) {
                            is MarkResult.Ok -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { snackbar.showSnackbar("Ponto ${vm.state.value.stops.size} marcado") }
                            }
                            MarkResult.NoGps -> scope.launch { snackbar.showSnackbar("Aguarde o GPS ficar preciso para marcar o ponto.") }
                        }
                    },
                    enabled = gpsOk,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = br.com.inforprimetecnologia.vaidebuzao.ui.theme.Brand.Yellow400, contentColor = androidx.compose.ui.graphics.Color(0xFF1F1600)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                ) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Parou num ponto", style = MaterialTheme.typography.titleLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VdbSecondaryButton("Desfazer", { vm.undoStop() }, Modifier.weight(1f), enabled = state.stops.isNotEmpty(), icon = Icons.AutoMirrored.Filled.Undo)
                    VdbPrimaryButton(
                        "Finalizar",
                        {
                            if (!vm.finish()) scope.launch { snackbar.showSnackbar("Marque pelo menos 2 pontos antes de finalizar.") }
                        },
                        Modifier.weight(1f),
                        icon = Icons.Filled.CheckCircle,
                    )
                }
                Text(
                    "Mantenha o app aberto. Se sair dele ou desligar a tela, a gravação pausa (fica salva como rascunho).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Parar a gravação?") },
            text = { Text("O que foi gravado até agora fica salvo como rascunho. Você pode continuar depois em \"Gravar linha\".") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.pauseAsDraft()
                    onBack()
                }) { Text("Parar e sair") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar gravando") } },
        )
    }
}

@Composable
private fun RecordReview(vm: RecordLineViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val ext = VdbTheme.colors
    val line = remember(state.id, state.stops, state.trace) { state.toLine() }
    val github = remember(line) { vm.githubNewFileUrl(line) }
    Scaffold(topBar = { VdbTopBar("Linha gravada", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            VdbMap(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                darkTheme = VdbTheme.isDark,
                paths = listOf(MapPath(state.trace, ext.lineDefault.toHex())),
                markers = state.stops.map { MapMarker(it, ext.stop.toHex()) },
                camera = CameraRequest(1, bounds = state.trace.ifEmpty { state.stops }),
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(state.number, null, large = true)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Sentido ${state.to}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.stops.size} pontos · ${Geo.formatDistance(state.distanceMeters)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                InfoBanner(
                    "Pronto! A linha já vale neste celular: aparece na aba Linhas (\"gravada por você\"), nos pontos e nas rotas.",
                    icon = Icons.Filled.CheckCircle,
                )
                SectionHeader("Enviar para o app de todo mundo")
                Text(SHARE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (github != null) {
                    VdbPrimaryButton("Enviar para o projeto (GitHub)", { openUrl(context, github) }, Modifier.fillMaxWidth(), icon = Icons.Filled.CloudUpload)
                    Text(
                        "Abre o GitHub com o arquivo pronto. Lá, toque em \"Commit changes\". Precisa estar logado.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VdbSecondaryButton(
                    "Compartilhar arquivo",
                    { shareRecordedLine(context, line, vm.exportJson(line)) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Share,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                VdbAccentButton("Gravar a volta (sentido contrário)", { vm.recordReturn() }, Modifier.fillMaxWidth(), icon = Icons.Filled.SwapHoriz)
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Concluir") }
            }
        }
    }
}

@Composable
fun MyRecordingsScreen(vm: RecordLineViewModel, onBack: () -> Unit, onOpenLine: (String) -> Unit, onRecord: () -> Unit) {
    val recordings by vm.recordings.collectAsState()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Long?>(null) }
    Scaffold(topBar = { VdbTopBar("Minhas gravações", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        if (recordings.isEmpty()) {
            EmptyState(
                "Nenhuma linha gravada",
                "Grave uma linha enquanto anda de ônibus. Ela fica salva só neste celular.",
                Modifier.padding(pad),
                actionText = "Gravar linha",
                onAction = onRecord,
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(recordings, key = { it.line.id }) { r ->
                val l = r.line
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(l.number, null, large = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sentido ${l.to}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${l.stops.size} pontos · ${Geo.formatDistance(l.distanceMeters)} · ${l.date.split("-").reversed().joinToString("/")}" +
                                    if (r.finished) "" else " · rascunho",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.finished) MaterialTheme.colorScheme.onSurfaceVariant else VdbTheme.colors.warning,
                                fontWeight = if (r.finished) FontWeight.Normal else FontWeight.SemiBold,
                            )
                        }
                        IconButton(onClick = { toDelete = l.id }) { Icon(Icons.Filled.Delete, contentDescription = "Apagar gravação") }
                    }
                    if (r.finished) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { onOpenLine(l.number) }) { Text("Ver linha") }
                            vm.githubNewFileUrl(l)?.let { url -> TextButton(onClick = { openUrl(context, url) }) { Text("Enviar (GitHub)") } }
                            TextButton(onClick = { shareRecordedLine(context, l, vm.exportJson(l)) }) { Text("Compartilhar") }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            item {
                Text(SHARE_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        }
    }
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Apagar esta gravação?") },
            text = { Text("Ela sai deste celular. Se você já enviou para o projeto, a cópia enviada não é afetada.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id)
                    toDelete = null
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}
