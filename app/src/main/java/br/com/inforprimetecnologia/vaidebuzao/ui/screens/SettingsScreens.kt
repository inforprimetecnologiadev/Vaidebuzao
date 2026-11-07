package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.com.inforprimetecnologia.vaidebuzao.BuildConfig
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentAction
import br.com.inforprimetecnologia.vaidebuzao.core.consent.ConsentClock
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitMeta
import br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadge
import br.com.inforprimetecnologia.vaidebuzao.ui.components.ListRow
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SectionHeader
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbAccentButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbCard
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbLogo
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbPrimaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbSecondaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoPill
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SkeletonList
import br.com.inforprimetecnologia.vaidebuzao.ui.state.AppViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.state.OPT_HISTORY
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.Brand
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.ThemeMode
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: AppViewModel,
    onOpenPrivacy: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDesignSystem: () -> Unit,
) {
    val context = LocalContext.current
    val settings by app.settings.collectAsState()
    val legal by app.legal.collectAsState()
    Scaffold(topBar = { VdbTopBar("Ajustes") }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            SectionHeader("Aparência", Modifier.padding(horizontal = 20.dp))
            Text("Tema", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            val modes = ThemeMode.entries
            val icons = mapOf(ThemeMode.SYSTEM to Icons.Filled.BrightnessAuto, ThemeMode.LIGHT to Icons.Filled.LightMode, ThemeMode.DARK to Icons.Filled.DarkMode)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                modes.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings?.themeMode == mode,
                        onClick = { app.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        icon = { Icon(icons.getValue(mode), contentDescription = null, modifier = Modifier.padding(end = 2.dp)) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(
                "Automático segue o tema do seu celular.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader("Privacidade e termos", Modifier.padding(horizontal = 20.dp))
            ListRow("Central de privacidade", subtitle = "Seus dados, consentimentos e direitos (LGPD)", icon = Icons.Filled.PrivacyTip, onClick = onOpenPrivacy)
            ListRow("Termos de Uso", subtitle = legal?.let { "Versão ${it.terms.version}" }, icon = Icons.Filled.Gavel, onClick = { onOpenDoc("termos") })
            ListRow("Política de Privacidade", subtitle = legal?.let { "Versão ${it.privacy.version}" }, icon = Icons.Filled.Description, onClick = { onOpenDoc("privacidade") })
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader("Ajuda", Modifier.padding(horizontal = 20.dp))
            ListRow(
                "Reportar problema ou sugerir melhoria",
                subtitle = "Linha errada, ponto faltando, erro no app",
                icon = Icons.Filled.BugReport,
                onClick = { sendEmail(context, legal?.contactEmail ?: "inforprimetecnologia@gmail.com", "Vai de Buzão ${BuildConfig.VERSION_NAME}: problema/sugestão") },
            )
            ListRow("Sobre o Vai de Buzão", subtitle = "Versão ${BuildConfig.VERSION_NAME}", icon = Icons.Filled.Info, onClick = onOpenAbout)
            if (BuildConfig.DEBUG) {
                ListRow("Design System (catálogo)", subtitle = "Só na versão de teste", icon = Icons.Filled.Palette, onClick = onOpenDesignSystem)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun PrivacyCenterScreen(app: AppViewModel, onBack: () -> Unit, onOpenHistory: () -> Unit, onOpenDoc: (String) -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.collectAsState()
    val records by app.consentRecords.collectAsState()
    val legal by app.legal.collectAsState()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    val lastAccept = records?.firstOrNull { it.action == ConsentAction.ACEITO }

    Scaffold(topBar = { VdbTopBar("Central de privacidade", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                InfoBanner(
                    "Tudo o que o app guarda sobre você fica somente neste celular. Não temos servidor nem cadastro.",
                    icon = Icons.Filled.VerifiedUser,
                )
                if (lastAccept != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("Último aceite: ${ConsentClock.display(lastAccept.localDateTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionHeader("Consentimentos", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            ListRow(
                "Salvar histórico de buscas",
                subtitle = "Opcional. Guarda seus destinos recentes só neste aparelho.",
                icon = Icons.Filled.History,
                trailing = {
                    Switch(checked = settings?.historyEnabled == true, onCheckedChange = { app.setHistoryEnabled(it) })
                },
            )
            ListRow(
                "Permissão de localização",
                subtitle = "Ligue ou desligue nas configurações do Android",
                icon = Icons.Filled.LocationOn,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
            ListRow("Meus consentimentos", subtitle = "Histórico com data e hora de cada decisão", icon = Icons.Filled.Description, onClick = onOpenHistory)

            SectionHeader("Seus direitos (LGPD, art. 18)", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            ListRow(
                "Exportar meus dados",
                subtitle = "Acesso e portabilidade: arquivo JSON com tudo o que o app guarda",
                icon = Icons.Filled.Share,
                onClick = {
                    scope.launch {
                        val json = app.exportJson()
                        val dir = File(context.cacheDir, "exportacao").apply { mkdirs() }
                        val file = File(dir, "vai-de-buzao-meus-dados.json").apply { writeText(json) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Meus dados – Vai de Buzão")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Exportar meus dados"))
                    }
                },
            )
            ListRow("Revogar consentimento", subtitle = "Volta para a tela de termos", icon = Icons.AutoMirrored.Filled.Undo, onClick = { confirm = "revogar" })
            ListRow(
                "Apagar meus dados",
                subtitle = "Remove favoritos, histórico, preferências e registros",
                icon = Icons.Filled.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = { confirm = "apagar" },
            )

            SectionHeader("Contato", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            ListRow(
                "Falar com o responsável pelos dados",
                subtitle = legal?.privacyEmail,
                icon = Icons.Filled.Email,
                onClick = { sendEmail(context, legal?.privacyEmail ?: "", "Vai de Buzão: solicitação LGPD") },
            )
            ListRow(
                "Autoridade Nacional de Proteção de Dados (ANPD)",
                subtitle = "www.gov.br/anpd",
                icon = Icons.Filled.Gavel,
                onClick = { openUrl(context, "https://www.gov.br/anpd") },
            )
            ListRow("Política de Privacidade", icon = Icons.Filled.PrivacyTip, onClick = { onOpenDoc("privacidade") })
            Spacer(Modifier.height(32.dp))
        }
    }

    when (confirm) {
        "revogar" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Revogar consentimento?") },
            text = { Text("O app vai parar de funcionar até você aceitar os termos de novo. Seu histórico de buscas será apagado. A revogação fica registrada com data e hora.") },
            confirmButton = { TextButton(onClick = { confirm = null; app.revoke() }) { Text("Revogar") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
        "apagar" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Apagar todos os seus dados?") },
            text = { Text("Favoritos, histórico, preferências e o registro de consentimentos serão apagados deste aparelho. Fica só um registro mínimo da exclusão. Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    app.wipeAll()
                    Toast.makeText(context, "Seus dados foram apagados.", Toast.LENGTH_SHORT).show()
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun ConsentHistoryScreen(app: AppViewModel, onBack: () -> Unit) {
    val records by app.consentRecords.collectAsState()
    Scaffold(topBar = { VdbTopBar("Meus consentimentos", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        val list = records
        when {
            list == null -> SkeletonList(4)
            list.isEmpty() -> EmptyState("Sem registros", "Nenhuma decisão registrada ainda.", Modifier.padding(pad))
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { r ->
                    VdbCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.action.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            InfoPill("v${r.appVersion}")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(ConsentClock.display(r.localDateTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("UTC: ${r.utcDateTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Termos v${r.termsVersion} · Privacidade v${r.privacyVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        r.optional[OPT_HISTORY]?.let {
                            Text("Histórico de buscas: ${if (it) "permitido" else "não permitido"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Registro: ${r.id.take(8)}…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(meta: TransitMeta?, onBack: () -> Unit, onOpenDoc: (String) -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { VdbTopBar("Sobre", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VdbLogo(140.dp)
            Spacer(Modifier.height(12.dp))
            Text("Vai de Buzão", style = MaterialTheme.typography.headlineMedium)
            Text("Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                "Feito para quem anda de ônibus em Cuiabá, Várzea Grande e região. Gratuito, sem anúncios e sem rastreadores.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            InfoBanner(
                "App independente: não é oficial nem ligado às prefeituras, ao Governo do Estado ou às empresas de ônibus.",
                icon = Icons.Filled.Info,
                warning = true,
            )
            Spacer(Modifier.height(16.dp))
            VdbCard(Modifier.fillMaxWidth()) {
                Text("Dados de ônibus", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(meta?.source ?: "Sem base de dados nesta versão", style = MaterialTheme.typography.bodySmall)
                if (meta != null) {
                    Text("Versão dos dados: ${meta.dataVersion} · gerado em ${meta.generatedAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pontos, linhas e trajetos: © colaboradores do OpenStreetMap, licença ODbL. Mapas: OpenFreeMap, © OpenMapTiles. " +
                        "Busca de endereços: Nominatim/OpenStreetMap.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { openUrl(context, "https://www.openstreetmap.org/copyright") }) { Text("Ver licença do OpenStreetMap") }
                TextButton(onClick = { openUrl(context, "https://www.openstreetmap.org/fixthemap") }) { Text("Ajudar a corrigir o mapa") }
            }
            Spacer(Modifier.height(12.dp))
            VdbCard(Modifier.fillMaxWidth()) {
                Text("Software de código aberto", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                listOf(
                    "Kotlin, AndroidX, Jetpack Compose e Material 3 — Apache 2.0",
                    "MapLibre Native Android — BSD 2-Clause",
                    "kotlinx.coroutines e kotlinx.serialization — Apache 2.0",
                    "Google Play Services Location — Termos do Android SDK",
                    "Fontes Inter e Montserrat — SIL Open Font License 1.1",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                VdbSecondaryButton("Termos", { onOpenDoc("termos") }, Modifier.weight(1f))
                VdbSecondaryButton("Privacidade", { onOpenDoc("privacidade") }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { openUrl(context, BuildConfig.REPO_URL) }) { Text("Código-fonte e versões") }
            Text("© InforPrime Tecnologia · Cuiabá – MT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Catálogo do design system (somente na versão de teste). */
@Composable
fun DesignSystemScreen(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ext = VdbTheme.colors
    Scaffold(topBar = { VdbTopBar("Design System", onBack = onBack) }, containerColor = c.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cores", style = MaterialTheme.typography.titleLarge)
            val swatches = listOf(
                "primary" to c.primary, "secondary" to c.secondary, "tertiary" to c.tertiary, "surface" to c.surface,
                "background" to c.background, "error" to c.error, "success" to ext.success, "warning" to ext.warning,
                "stop" to ext.stop, "walk" to ext.walk, "Navy900" to Brand.Navy900, "Yellow400" to Brand.Yellow400,
            )
            swatches.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, color) ->
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(48.dp)) {}
                            Text(name, style = MaterialTheme.typography.labelSmall)
                            Text(color.toHex(), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("Tipografia", style = MaterialTheme.typography.titleLarge)
            Text("Display · Vai de Buzão", style = MaterialTheme.typography.displaySmall)
            Text("Headline · Para onde vamos?", style = MaterialTheme.typography.headlineSmall)
            Text("Title · Pontos perto de você", style = MaterialTheme.typography.titleLarge)
            Text("Body · Caminhe até o ponto e pegue a linha 101.", style = MaterialTheme.typography.bodyLarge)
            Text("Label · 8 MIN A PÉ", style = MaterialTheme.typography.labelLarge)
            Text("Componentes", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LineBadge("101", null)
                LineBadge("024", "#FFC800")
                LineBadge("305", "#C0392B")
                LineBadge("612", "#1B7A3D", large = true)
            }
            VdbPrimaryButton("Botão principal", {}, Modifier.fillMaxWidth())
            VdbAccentButton("Botão de destaque", {}, Modifier.fillMaxWidth())
            VdbSecondaryButton("Botão secundário", {}, Modifier.fillMaxWidth())
            InfoBanner("Banner informativo", icon = Icons.Filled.Info)
            InfoBanner("Banner de alerta", icon = Icons.Filled.Info, warning = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("8 min a pé", Icons.Filled.LocationOn)
                InfoPill("espera ~10 min")
            }
            VdbCard(Modifier.fillMaxWidth()) { Text("Cartão (VdbCard)") }
            SkeletonList(2)
            EmptyState("Estado vazio", "Mensagem explicando o que aconteceu e o que fazer.", actionText = "Ação", onAction = {})
            Spacer(Modifier.width(1.dp))
        }
    }
}
