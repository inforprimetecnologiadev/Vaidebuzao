package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.data.FavoriteType
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadge
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SkeletonList
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.map.CameraRequest
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapMarker
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapPath
import br.com.inforprimetecnologia.vaidebuzao.ui.map.VdbMap
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

@Composable
fun LinesScreen(vm: TransitViewModel, onOpenLine: (String) -> Unit) {
    val network by vm.network.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { VdbTopBar("Linhas") }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Número, nome ou bairro") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Limpar") }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            val net = network
            when {
                net == null -> SkeletonList(6)
                net.lines.isEmpty() -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    EmptyState(
                        "Ainda não temos as linhas",
                        "Os pontos de ônibus de Cuiabá e Várzea Grande já estão no app, mas as linhas ainda não foram " +
                            "cadastradas nos dados abertos (OpenStreetMap). Estamos trabalhando para incluir. Você sabe " +
                            "o itinerário de alguma linha? Conte pra gente.",
                        actionText = "Como ajudar",
                        onAction = { openUrl(context, br.com.inforprimetecnologia.vaidebuzao.BuildConfig.REPO_URL + "/blob/main/data/linhas/LEIA-ME.md") },
                    )
                }
                else -> {
                    val lines = remember(net, query) { net.searchLines(query, limit = 500) }
                    if (lines.isEmpty()) {
                        EmptyState("Nada encontrado", "Tente outro número ou nome de bairro.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    "${lines.size} ${if (lines.size == 1) "linha" else "linhas"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                            }
                            items(lines, key = { it.ref }) { line ->
                                PatternRow(
                                    line.patterns.first(),
                                    onClick = { onOpenLine(line.ref) },
                                    extra = if (line.patterns.size > 1) "${line.patterns.size} sentidos" else null,
                                )
                                HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(vm: TransitViewModel, ref: String, onBack: () -> Unit, onOpenStop: (Int) -> Unit) {
    val network by vm.network.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val line = network?.lineByRef(ref)
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val isFav = favorites.any { it.type == FavoriteType.LINHA && it.ref == ref }
    val ext = VdbTheme.colors

    Scaffold(
        topBar = {
            VdbTopBar("Linha $ref", onBack = onBack, actions = {
                if (line != null) {
                    IconButton(onClick = { vm.toggleFavorite(FavoriteType.LINHA, ref, "Linha $ref · ${line.name}", null) }) {
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
        val net = network
        if (net == null || line == null) {
            EmptyState("Linha não encontrada", "Esta linha não está na base de dados atual.", Modifier.padding(pad))
            return@Scaffold
        }
        val pattern = line.patterns[selected.coerceIn(0, line.patterns.lastIndex)]
        val stops = remember(pattern) { pattern.stopIds.mapNotNull { net.stopById(it) } }
        val color = pattern.colorHex ?: ext.lineDefault.toHex()
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(line.ref, line.colorHex, large = true)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(line.name, style = MaterialTheme.typography.titleMedium)
                        pattern.operator?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (line.patterns.size > 1) {
                item {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                        itemsIndexed(line.patterns) { i, p ->
                            FilterChip(
                                selected = i == selected,
                                onClick = { selected = i },
                                label = { Text(p.directionLabel) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
            item {
                VdbMap(
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp),
                    darkTheme = VdbTheme.isDark,
                    paths = listOf(MapPath(pattern.shape.ifEmpty { stops.map { it.point } }, color)),
                    markers = stops.map { MapMarker(it.point, color) },
                    camera = CameraRequest(pattern.id, bounds = pattern.shape.ifEmpty { stops.map { it.point } }),
                )
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        listOfNotNull(pattern.from?.let { "De $it" }, pattern.to?.let { "para $it" }).joinToString(" ").ifBlank { pattern.name },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    pattern.headwayMinutes?.let {
                        Text("Intervalo médio: ~$it min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pattern.inferredStops) {
                        Spacer(Modifier.height(8.dp))
                        InfoBanner("Os pontos desta linha foram estimados pelo traçado do itinerário e podem não ser exatos.", icon = Icons.Filled.Info, warning = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${stops.size} pontos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(stops) { i, s ->
                StopRow(s, emptyList(), null, null, onClick = { onOpenStop(s.id) }, showLines = false)
                if (i < stops.lastIndex) HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
