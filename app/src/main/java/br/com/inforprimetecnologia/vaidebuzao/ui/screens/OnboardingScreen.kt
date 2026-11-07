package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbAccentButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbLogo
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.Brand
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val text: String)

private val pages = listOf(
    Page(Icons.Filled.Place, "O ponto mais perto de você", "Veja no mapa os pontos de ônibus ao seu redor, a distância e quanto tempo leva a pé."),
    Page(Icons.Filled.DirectionsBus, "Quais linhas passam ali", "Toque em um ponto para ver as linhas e o sentido de cada uma."),
    Page(Icons.Filled.Route, "Qual buzão pegar", "Diga para onde vai e o app mostra onde embarcar, qual linha pegar, onde descer e se tem baldeação."),
)

@Composable
fun OnboardingScreen(onPermissionResult: () -> Unit, onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val ext = VdbTheme.colors
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionResult()
        onFinish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ext.heroGradientTop, ext.heroGradientBottom))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (pager.currentPage < pages.size) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.size) } }) {
                        Text("Pular", color = Color.White)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (index < pages.size) {
                        val p = pages[index]
                        if (index == 0) {
                            VdbLogo(180.dp)
                        } else {
                            Box(
                                Modifier.size(140.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                VdbLogo(110.dp, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Icon(p.icon, contentDescription = null, tint = Brand.Yellow400, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(p.text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.88f), textAlign = TextAlign.Center)
                    } else {
                        Box(
                            Modifier.size(140.dp).clip(CircleShape).background(Brand.Yellow400.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Brand.Yellow400, modifier = Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "Podemos usar sua localização?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Ela serve só para achar os pontos perto de você e calcular rotas. Fica no seu celular, " +
                                "não é gravada e não é enviada para nós. Só usamos com o app aberto.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Se preferir, você pode escolher o local no mapa com um toque longo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size + 1) { i ->
                    val selected = pager.currentPage == i
                    val w by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (selected) Brand.Yellow400 else Color.White.copy(alpha = 0.4f), label = "dotColor")
                    Box(Modifier.padding(4.dp).height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (pager.currentPage < pages.size) {
                    VdbAccentButton("Continuar", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(56.dp))
                } else {
                    VdbAccentButton(
                        "Permitir localização",
                        {
                            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Filled.MyLocation,
                    )
                    TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Agora não", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
