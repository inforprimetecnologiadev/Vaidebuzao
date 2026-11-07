package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SkeletonList
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbLogo
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbPrimaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbSecondaryButton
import br.com.inforprimetecnologia.vaidebuzao.ui.map.CameraRequest
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapMarker
import br.com.inforprimetecnologia.vaidebuzao.ui.map.VdbMap
import br.com.inforprimetecnologia.vaidebuzao.ui.state.LocationStatus
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: TransitViewModel, onOpenStop: (Int) -> Unit, onOpenSearch: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val network by vm.network.collectAsState()
    val stopsGeoJson by vm.stopsGeoJson.collectAsState()
    val location by vm.location.collectAsState()
    val manual by vm.manualOrigin.collectAsState()
    val reference by vm.reference.collectAsState()
    val status by vm.locationStatus.collectAsState()
    val nearby by vm.nearby.collectAsState()
    val ext = VdbTheme.colors

    var cameraId by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<CameraRequest?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshLocation()
    }

    LaunchedEffect(Unit) { if (location == null) vm.refreshLocation() }
    LaunchedEffect(reference) {
        reference?.let { camera = CameraRequest(++cameraId, center = it, zoom = 16.0) }
    }

    val peek = 300.dp
    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = peek,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
                Text(
                    "Pontos perto de você",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
                )
                Text(
                    when {
                        manual != null -> "A partir do local escolhido no mapa"
                        location != null -> "A partir da sua localização"
                        else -> "Ative a localização ou toque e segure no mapa"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                val net = network
                val list = nearby
                when {
                    net == null -> SkeletonList(3)
                    net.isEmpty -> EmptyState(
                        "Ainda não temos dados de ônibus",
                        "A base de pontos e linhas ainda não foi carregada nesta versão do app. Atualize o app em breve.",
                    )
                    reference == null && status == LocationStatus.SEARCHING -> SkeletonList(3)
                    reference == null && status == LocationStatus.NO_PERMISSION -> PermissionCard(
                        onAllow = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                    )
                    reference == null && status == LocationStatus.DISABLED -> GpsOffCard(
                        onOpenSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                        onRetry = { vm.refreshLocation() },
                    )
                    reference == null -> Column(Modifier.padding(horizontal = 20.dp)) {
                        InfoBanner("Não conseguimos sua localização agora. Tente de novo ou toque e segure no mapa para escolher um local.", icon = Icons.Filled.LocationOff, warning = true)
                        Spacer(Modifier.height(12.dp))
                        VdbSecondaryButton("Tentar de novo", { vm.refreshLocation() }, icon = Icons.Filled.MyLocation)
                    }
                    list.isNullOrEmpty() -> EmptyState("Nenhum ponto por perto", "Não encontramos pontos de ônibus num raio de 3 km deste local.")
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                        items(list, key = { it.stop.id }) { n ->
                            StopRow(
                                stop = n.stop,
                                patterns = net.patternsAtStop(n.stop.id),
                                distanceMeters = n.distanceMeters,
                                walkMinutes = n.walkMinutes,
                                onClick = { onOpenStop(n.stop.id) },
                            )
                            HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            VdbMap(
                modifier = Modifier.fillMaxSize(),
                darkTheme = VdbTheme.isDark,
                stopsGeoJson = stopsGeoJson,
                stopColorHex = ext.stop.toHex(),
                stopStrokeHex = ext.stopStroke.toHex(),
                userLocation = location,
                userColorHex = ext.userLocation.toHex(),
                markers = listOfNotNull(manual?.let { MapMarker(it, MaterialTheme.colorScheme.tertiary.toHex()) }),
                camera = camera,
                bottomPaddingPx = with(density) { peek.roundToPx() },
                onStopClick = onOpenStop,
                onLongPress = { vm.setManualOrigin(it) },
            )
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                Surface(
                    onClick = onOpenSearch,
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        VdbLogo(40.dp, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Para onde vamos?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.Search, contentDescription = "Buscar destino", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (manual != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text("Usando o local escolhido no mapa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            IconButton(onClick = { vm.setManualOrigin(null); vm.refreshLocation() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Voltar a usar minha localização", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
            }
            SmallFloatingActionButton(
                onClick = {
                    vm.setManualOrigin(null)
                    if (vm.locationStatus.value == LocationStatus.NO_PERMISSION) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        vm.refreshLocation()
                        location?.let { camera = CameraRequest(++cameraId, center = it, zoom = 16.0) }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = peek + 16.dp).size(52.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Centralizar na minha localização")
            }
        }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        InfoBanner(
            "Para mostrar os pontos perto de você, o app precisa da sua localização. Ela fica só no seu celular.",
            icon = Icons.Filled.MyLocation,
        )
        Spacer(Modifier.height(12.dp))
        VdbPrimaryButton("Permitir localização", onAllow, Modifier.fillMaxWidth(), icon = Icons.Filled.MyLocation)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ou toque e segure em qualquer lugar do mapa para escolher um local.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GpsOffCard(onOpenSettings: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        InfoBanner("A localização do celular está desligada.", icon = Icons.Filled.LocationOff, warning = true)
        Spacer(Modifier.height(12.dp))
        Row {
            VdbPrimaryButton("Abrir configurações", onOpenSettings, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            VdbSecondaryButton("Tentar", onRetry)
        }
    }
}
