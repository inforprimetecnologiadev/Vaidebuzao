package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.core.geo.Geo
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Itinerary
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Leg
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoPill
import br.com.inforprimetecnologia.vaidebuzao.ui.components.LineBadge
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SkeletonList
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbCard
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.components.parseHexColor
import br.com.inforprimetecnologia.vaidebuzao.ui.map.CameraRequest
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapMarker
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapPath
import br.com.inforprimetecnologia.vaidebuzao.ui.map.VdbMap
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

private const val ESTIMATE_NOTE =
    "Tempos estimados: não temos horários oficiais nem a posição dos ônibus em tempo real. Confira o letreiro antes de embarcar."

fun itinerarySummary(it: Itinerary): String = when {
    it.isWalkOnly -> "Só caminhando"
    it.transfers == 0 -> "Direto, sem baldeação"
    it.transfers == 1 -> "1 baldeação"
    else -> "${it.transfers} baldeações"
}

@Composable
fun RouteResultsScreen(vm: TransitViewModel, onBack: () -> Unit, onEdit: () -> Unit, onOpen: (Int) -> Unit) {
    val trip by vm.trip.collectAsState()
    Scaffold(
        topBar = { VdbTopBar("Opções de rota", onBack = onBack, actions = { TextButton(onClick = onEdit) { Text("Alterar") } }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("De: ${trip.origin?.name ?: "—"}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Para: ${trip.destination?.name ?: "—"}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                InfoBanner(ESTIMATE_NOTE, icon = Icons.Filled.Info)
            }
            val results = trip.results
            when {
                trip.loading || results == null -> SkeletonList(4)
                results.isEmpty() -> EmptyState(
                    "Não achamos uma rota de ônibus",
                    if (vm.network.value?.lines.isNullOrEmpty()) {
                        "As linhas de ônibus da região ainda não estão na base de dados do app (só os pontos). " +
                            "Assim que forem cadastradas, as rotas aparecem aqui."
                    } else {
                        "Não há linha cadastrada passando perto da origem e do destino ao mesmo tempo. " +
                            "Tente um local mais perto de uma avenida."
                    },
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(results) { index, it ->
                        RouteOptionCard(it, best = index == 0, onClick = { onOpen(index) })
                    }
                }
            }
        }
    }
}

@Composable
fun RouteOptionCard(it: Itinerary, best: Boolean, onClick: () -> Unit) {
    val desc = buildString {
        append("${Geo.formatMinutes(it.totalMinutes)}. ${itinerarySummary(it)}. ")
        it.rides.forEach { r -> append("Linha ${r.pattern.ref}, ${r.pattern.directionLabel}. ") }
        append("${Geo.formatDistance(it.walkMeters)} a pé.")
    }
    VdbCard(onClick = onClick, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = desc }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (best) {
                    Text("MELHOR OPÇÃO", style = MaterialTheme.typography.labelSmall, color = VdbTheme.colors.success, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                Text("~${Geo.formatMinutes(it.totalMinutes)}", style = MaterialTheme.typography.headlineSmall)
                Text(itinerarySummary(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            it.legs.forEachIndexed { i, leg ->
                if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                when (leg) {
                    is Leg.Walk -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = VdbTheme.colors.walk, modifier = Modifier.size(20.dp))
                        Text("${leg.minutes.toInt().coerceAtLeast(1)}", style = MaterialTheme.typography.labelSmall, color = VdbTheme.colors.walk)
                    }
                    is Leg.Ride -> LineBadge(leg.pattern.ref, leg.pattern.colorHex)
                }
            }
        }
        it.rides.firstOrNull()?.let { r ->
            Spacer(Modifier.height(10.dp))
            Text(
                "Embarque em ${r.board.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("${Geo.formatDistance(it.walkMeters)} a pé", Icons.AutoMirrored.Filled.DirectionsWalk)
            val wait = it.rides.sumOf { r -> r.waitMinutes }
            if (wait > 0) InfoPill("espera ~${wait.toInt()} min", Icons.Filled.Schedule)
        }
    }
}

@Composable
fun ItineraryScreen(vm: TransitViewModel, index: Int, onBack: () -> Unit, onOpenStop: (Int) -> Unit) {
    val trip by vm.trip.collectAsState()
    val itinerary = trip.results?.getOrNull(index)
    val ext = VdbTheme.colors
    Scaffold(topBar = { VdbTopBar("Seu trajeto", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        if (itinerary == null) {
            EmptyState("Trajeto indisponível", "Volte e calcule a rota novamente.", Modifier.padding(pad))
            return@Scaffold
        }
        val walkHex = ext.walk.toHex()
        val paths = remember(itinerary, walkHex) {
            itinerary.legs.map { leg ->
                when (leg) {
                    is Leg.Walk -> MapPath(listOf(leg.from, leg.to), walkHex, walking = true)
                    is Leg.Ride -> MapPath(leg.stops.map { it.point }, leg.pattern.colorHex ?: "#1446C8")
                }
            }
        }
        val allPoints = paths.flatMap { it.points }
        val first = itinerary.legs.first()
        val last = itinerary.legs.last()
        val startPoint = if (first is Leg.Walk) first.from else (first as Leg.Ride).board.point
        val endPoint = if (last is Leg.Walk) last.to else (last as Leg.Ride).alight.point
        var expanded by rememberSaveable { mutableStateOf(-1) }

        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                VdbMap(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    darkTheme = VdbTheme.isDark,
                    paths = paths,
                    markers = listOf(
                        MapMarker(startPoint, MaterialTheme.colorScheme.primary.toHex()),
                        MapMarker(endPoint, ext.stop.toHex()),
                    ) + itinerary.rides.flatMap { r -> listOf(MapMarker(r.board.point, "#FFFFFF"), MapMarker(r.alight.point, "#FFFFFF")) },
                    camera = CameraRequest(index + 1, bounds = allPoints),
                    onStopClick = null,
                )
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Text("~${Geo.formatMinutes(itinerary.totalMinutes)}", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${itinerarySummary(itinerary)} · ${Geo.formatDistance(itinerary.walkMeters)} a pé",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(ESTIMATE_NOTE, icon = Icons.Filled.Info)
                }
            }
            itemsIndexed(itinerary.legs) { i, leg ->
                when (leg) {
                    is Leg.Walk -> TimelineStep(
                        color = ext.walk,
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        dashed = true,
                        title = if (i == itinerary.legs.lastIndex) "Caminhe até ${leg.toName}" else "Caminhe até ${leg.toName}",
                        subtitle = "${Geo.formatDistance(leg.meters)} · ${Geo.formatMinutes(leg.minutes)}",
                        isLast = false,
                    )
                    is Leg.Ride -> {
                        val color = parseHexColor(leg.pattern.colorHex, ext.lineDefault)
                        TimelineStep(
                            color = color,
                            icon = Icons.Filled.DirectionsBus,
                            title = "Pegue a linha ${leg.pattern.ref.ifBlank { leg.pattern.name }}",
                            subtitle = "${leg.pattern.directionLabel}\nEmbarque: ${leg.board.displayName}",
                            badge = { LineBadge(leg.pattern.ref, leg.pattern.colorHex, large = true) },
                            extra = {
                                Column {
                                    Text(
                                        "Espere ~${leg.waitMinutes.toInt()} min · viagem ~${Geo.formatMinutes(leg.rideMinutes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = { expanded = if (expanded == i) -1 else i }) {
                                        Text(if (expanded == i) "Esconder pontos" else "Ver os ${leg.stopCount} pontos do trajeto")
                                    }
                                    if (expanded == i) {
                                        leg.stops.forEachIndexed { si, s ->
                                            Text(
                                                "${si + 1}. ${s.displayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (si == 0 || si == leg.stops.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        "Desça em: ${leg.alight.displayName} (após ${leg.stopCount} ${if (leg.stopCount == 1) "ponto" else "pontos"})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (leg.pattern.inferredStops) {
                                        Text("Itinerário aproximado a partir do traçado da linha.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            isLast = false,
                            onClick = { onOpenStop(leg.board.id) },
                        )
                        if (i + 1 < itinerary.legs.size && itinerary.legs[i + 1] is Leg.Ride) {
                            TimelineStep(color = ext.walk, icon = Icons.Filled.SyncAlt, title = "Baldeação no mesmo ponto", subtitle = null, isLast = false)
                        }
                    }
                }
            }
            item {
                TimelineStep(
                    color = ext.stop,
                    icon = Icons.Filled.Place,
                    title = "Chegada: ${trip.destination?.name ?: "destino"}",
                    subtitle = null,
                    isLast = true,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TimelineStep(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    isLast: Boolean,
    dashed: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp)) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            if (!isLast) {
                Box(
                    Modifier
                        .weight(1f)
                        .width(if (dashed) 2.dp else 4.dp)
                        .background(color.copy(alpha = if (dashed) 0.45f else 0.9f)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 18.dp, top = 6.dp)
                .then(if (onClick != null) Modifier else Modifier),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                badge?.invoke()
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            extra?.let {
                Spacer(Modifier.height(6.dp))
                it()
            }
        }
    }
}
