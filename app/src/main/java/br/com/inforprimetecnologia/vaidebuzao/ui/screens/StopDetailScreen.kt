package br.com.inforprimetecnologia.vaidebuzao.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.data.FavoriteType
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadge
import br.com.inforprimetecnologia.vaidebuzao.ui.components.ListRow
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SectionHeader
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbAccentButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbSecondaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.map.CameraRequest
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapMarker
import br.com.inforprimetecnologia.vaidebuzao.ui.map.VdbMap
import br.com.inforprimetecnologia.vaidebuzao.ui.state.Endpoint
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

@Composable
fun StopDetailScreen(
    vm: TransitViewModel,
    stopId: Int,
    onBack: () -> Unit,
    onOpenPattern: (Int) -> Unit,
    onGoFromHere: () -> Unit,
    onReport: () -> Unit,
) {
    val context = LocalContext.current
    val network by vm.network.collectAsState()
    val reference by vm.reference.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val net = network
    val stop = net?.stopById(stopId)
    val isFav = favorites.any { it.type == FavoriteType.PONTO && it.ref == stopId.toString() }

    Scaffold(
        topBar = {
            VdbTopBar("Ponto de ônibus", onBack = onBack, actions = {
                if (stop != null) {
                    IconButton(onClick = { vm.toggleFavorite(FavoriteType.PONTO, stopId.toString(), stop.displayName, stop.point) }) {
                        Icon(
                            if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos",
                            tint = if (isFav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (net == null || stop == null) {
            EmptyState("Ponto não encontrado", "Este ponto não está na base de dados atual.", Modifier.padding(pad))
            return@Scaffold
        }
        val patterns = remember(stopId, net) { net.patternsAtStop(stopId) }
        val distance = reference?.let { Geo.distanceMeters(it, stop.point) }
        val ext = VdbTheme.colors
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                VdbMap(
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)),
                    darkTheme = VdbTheme.isDark,
                    userLocation = reference,
                    userColorHex = ext.userLocation.toHex(),
                    markers = listOf(MapMarker(stop.point, ext.stop.toHex())),
                    camera = CameraRequest(1, center = stop.point, zoom = 16.5),
                )
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(stop.displayName, style = MaterialTheme.typography.headlineSmall)
                    if (stop.name != null && stop.area != null) {
                        Text(stop.area!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (distance != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Geo.formatDistance(distance)} · ${Geo.formatMinutes(Geo.walkMinutes(distance * Geo.WALK_DETOUR_FACTOR))} a pé",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VdbAccentButton(
                            "Ir daqui",
                            {
                                vm.setOrigin(Endpoint(stop.displayName, stop.point))
                                vm.setDestination(null)
                                onGoFromHere()
                            },
                            Modifier.weight(1f),
                            icon = Icons.Filled.Flag,
                        )
                        VdbSecondaryButton(
                            "Como chegar",
                            { openWalkingDirections(context, stop.point, stop.displayName) },
                            Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        )
                    }
                }
            }
            item {
                SectionHeader("Linhas que passam aqui", Modifier.padding(horizontal = 20.dp))
            }
            if (patterns.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        InfoBanner(
                            "Ainda não temos as linhas deste ponto na base de dados. Se você sabe quais passam aqui, nos ajude a melhorar.",
                            icon = Icons.Filled.Info,
                            warning = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        VdbSecondaryButton("Ajudar a melhorar", onReport)
                    }
                }
            } else {
                items(patterns, key = { it.id }) { p ->
                    PatternRow(p, onClick = { onOpenPattern(p.id) })
                    HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
