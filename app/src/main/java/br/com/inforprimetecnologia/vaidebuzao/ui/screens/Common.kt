package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.transit.RoutePattern
import br.com.inforprimetecnologia.vaidebuzao.core.transit.Stop
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoPill
import br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadgeRow
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

/** Cor como "#RRGGBB" para o mapa. */
fun androidx.compose.ui.graphics.Color.toHex(): String {
    val argb = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
    )
    return String.format("#%06X", 0xFFFFFF and argb)
}

/** Linha de lista de um ponto de ônibus. */
@Composable
fun StopRow(
    stop: Stop,
    patterns: List<RoutePattern>,
    distanceMeters: Double?,
    walkMinutes: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLines: Boolean = true,
) {
    val refs = patterns.map { it.ref.ifBlank { "?" } to it.colorHex }.distinctBy { it.first }
    val a11y = buildString {
        append("Ponto ${stop.displayName}. ")
        if (distanceMeters != null) append("A ${Geo.formatDistance(distanceMeters)}, ${Geo.formatMinutes(walkMinutes ?: 0.0)} a pé. ")
        if (showLines) append(if (refs.isEmpty()) "Sem linhas cadastradas." else "Linhas: ${refs.joinToString { it.first }}.")
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = a11y }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(VdbTheme.colors.stop.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = VdbTheme.colors.stop)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stop.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!showLines) {
                stop.area?.takeIf { stop.name != null }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (refs.isEmpty()) {
                Text("Sem linhas cadastradas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineBadgeRow(refs, max = 5)
            }
        }
        if (distanceMeters != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Geo.formatDistance(distanceMeters), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(4.dp))
                InfoPill(Geo.formatMinutes(walkMinutes ?: 0.0), Icons.AutoMirrored.Filled.DirectionsWalk)
            }
        }
    }
}

fun openWalkingDirections(context: Context, to: GeoPoint, label: String) {
    val uri = Uri.parse("geo:${to.lat},${to.lon}?q=${to.lat},${to.lon}(${Uri.encode(label)})")
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Abrir com"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app de mapas encontrado.", Toast.LENGTH_SHORT).show()
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show()
    }
}

fun sendEmail(context: Context, to: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app de e-mail encontrado. Escreva para $to", Toast.LENGTH_LONG).show()
    }
}

/** Linha de lista de um sentido de linha: placa + nome + sentido. */
@Composable
fun PatternRow(p: RoutePattern, onClick: () -> Unit, modifier: Modifier = Modifier, extra: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadge(p.ref, p.colorHex, large = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name.ifBlank { "Linha ${p.ref}" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                add(p.directionLabel)
                p.headwayMinutes?.let { add("a cada ~$it min") }
                if (p.inferredStops) add("itinerário aproximado")
                extra?.let { add(it) }
            }.distinct().joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
