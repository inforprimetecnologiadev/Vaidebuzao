package br.com.inforprimetecnologia.vaidebuzao.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.R
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.Brand
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.Montserrat
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

/** Converte "#RRGGBB" (OSM/GTFS) em cor, com fallback. */
fun parseHexColor(hex: String?, fallback: Color): Color = runCatching {
    val clean = hex?.trim()?.removePrefix("#") ?: return fallback
    when (clean.length) {
        6 -> Color(("FF$clean").toLong(16))
        8 -> Color(clean.toLong(16))
        else -> fallback
    }
}.getOrDefault(fallback)

/** O logo oficial — única imagem/ilustração usada no app. */
@Composable
fun VdbLogo(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = "Logo Vai de Buzão") {
    Image(
        painter = painterResource(R.drawable.logo_vai_de_buzao),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/** "Placa" com o número da linha, como no letreiro do ônibus. */
@Composable
fun LineBadge(ref: String, colorHex: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    val bg = parseHexColor(colorHex, VdbTheme.colors.lineDefault)
    val fg = if (bg.luminance() > 0.45f) Color(0xFF0E1530) else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 10.dp else 8.dp))
            .background(bg)
            .padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 6.dp else 3.dp)
            .widthIn(min = if (large) 40.dp else 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ref.ifBlank { "—" },
            color = fg,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            fontFamily = Montserrat,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

/** Fileira de placas de linha, com "+N" quando passa do limite. */
@Composable
fun LineBadgeRow(lines: List<Pair<String, String?>>, max: Int = 6, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        lines.take(max).forEach { (ref, color) -> LineBadge(ref, color) }
        if (lines.size > max) {
            Text("+${lines.size - max}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VdbPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botão de destaque amarelo (ação principal da marca, ex.: "Para onde vamos?"). */
@Composable
fun VdbAccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow400, contentColor = Color(0xFF1F1600)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VdbSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VdbTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

@Composable
fun VdbCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = if (VdbTheme.isDark) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

/** Linha de lista com ícone, título, subtítulo e seta. Alvo de toque >= 56 dp. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Estado vazio/erro com o logo da marca (única ilustração do app). */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VdbLogo(96.dp, Modifier.alpha(0.9f), contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            VdbSecondaryButton(actionText, onAction)
        }
    }
}

/** Esqueleto de carregamento (shimmer suave). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * a / 0.5f)),
    )
}

@Composable
fun SkeletonList(rows: Int = 4) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(40.dp), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f))
                    SkeletonBlock(Modifier.fillMaxWidth(0.4f), 12.dp)
                }
            }
        }
    }
}

/** Aviso informativo (ex.: "tempos são estimativas"). */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, warning: Boolean = false) {
    val bg = if (warning) VdbTheme.colors.warningContainer else MaterialTheme.colorScheme.primaryContainer
    val fg = if (warning) VdbTheme.colors.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Pílula pequena de informação (ex.: "8 min a pé"). */
@Composable
fun InfoPill(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
