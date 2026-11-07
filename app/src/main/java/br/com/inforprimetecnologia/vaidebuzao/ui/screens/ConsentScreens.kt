package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.data.LegalBundle
import br.com.inforprimetecnologia.vaidebuzao.data.LegalDoc
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbLogo
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbPrimaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbSecondaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

/** Cabeçalho da marca em degradê, com o logo. */
@Composable
fun BrandHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val ext = VdbTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ext.heroGradientTop, ext.heroGradientBottom)))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VdbLogo(72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.semantics { heading() })
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String, required: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (required) "Obrigatório" else "Opcional — você pode mudar depois em Ajustes",
                style = MaterialTheme.typography.labelSmall,
                color = if (required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tela de aceite dos Termos e da Política (primeiro acesso e a cada nova versão). */
@Composable
fun ConsentScreen(legal: LegalBundle, isUpdate: Boolean, onAccept: (history: Boolean) -> Unit, onDecline: () -> Unit) {
    var terms by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var age by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf<String?>(null) }

    val doc = when (reading) {
        "termos" -> legal.terms
        "privacidade" -> legal.privacy
        else -> null
    }
    if (doc != null) {
        LegalDocScreen(doc, onBack = { reading = null })
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = pad.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            BrandHeader(
                title = if (isUpdate) "Atualizamos nossos termos" else "Antes de começar",
                subtitle = if (isUpdate) "Leia o que mudou e confirme para continuar." else "Sua privacidade é levada a sério aqui.",
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (isUpdate && legal.changes.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp)) {
                            Text("O que mudou", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(legal.changes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Text("Resumo em 30 segundos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                SummaryItem(Icons.Filled.Lock, "Sua localização é usada só no seu celular para achar pontos e rotas. Nós não a recebemos.")
                SummaryItem(Icons.Filled.Shield, "Sem cadastro, sem anúncios, sem rastreadores.")
                SummaryItem(Icons.Filled.WifiOff, "Pontos e linhas vêm de dados abertos (OpenStreetMap) e podem estar incompletos. Os tempos são estimativas.")
                SummaryItem(Icons.Filled.PrivacyTip, "Você pode ver, exportar, revogar e apagar seus dados a qualquer momento.")

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    VdbSecondaryButton("Termos de Uso", { reading = "termos" }, Modifier.weight(1f), icon = Icons.Filled.Description)
                    VdbSecondaryButton("Privacidade", { reading = "privacidade" }, Modifier.weight(1f), icon = Icons.Filled.PrivacyTip)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                CheckRow(terms, { terms = it }, "Li e aceito os Termos de Uso (versão ${legal.terms.version})", required = true)
                CheckRow(privacy, { privacy = it }, "Li e estou ciente da Política de Privacidade (versão ${legal.privacy.version})", required = true)
                CheckRow(age, { age = it }, "Tenho 18 anos ou mais, ou uso o app com autorização do meu responsável", required = true)
                CheckRow(history, { history = it }, "Salvar o histórico das minhas buscas neste aparelho", required = false)

                Spacer(Modifier.height(16.dp))
                VdbPrimaryButton(
                    "Aceitar e continuar",
                    onClick = { onAccept(history) },
                    enabled = terms && privacy && age,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Recusar", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "A data e a hora da sua decisão ficam registradas somente neste aparelho, conforme a LGPD (Lei nº 13.709/2018).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).navigationBarsPadding(),
                )
            }
        }
    }
}

/** Depois de recusar: explica e oferece rever ou sair. */
@Composable
fun DeclinedScreen(onReview: () -> Unit) {
    val activity = LocalContext.current as? Activity
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VdbLogo(120.dp)
            Spacer(Modifier.height(24.dp))
            Text("Tudo bem!", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "Sem aceitar os Termos de Uso e a Política de Privacidade não é possível usar o Vai de Buzão. " +
                    "Sua recusa foi registrada neste aparelho e nenhum dado foi enviado a ninguém.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            VdbPrimaryButton("Rever termos", onReview, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            VdbSecondaryButton("Sair do app", { activity?.finishAffinity() }, Modifier.fillMaxWidth())
        }
    }
}

/** Documento legal completo, com versão e resumo criptográfico. */
@Composable
fun LegalDocScreen(doc: LegalDoc, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { VdbTopBar(doc.type.title, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            MarkdownText(doc.markdown)
            Spacer(Modifier.height(24.dp))
            Text(
                "Versão ${doc.version} · SHA-256: ${doc.sha256.take(16)}…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
