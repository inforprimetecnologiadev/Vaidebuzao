package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.data.AppSettings
import br.com.inforprimetecnologia.vaidebuzao.data.FavoriteType
import br.com.inforprimetecnologia.vaidebuzao.ui.components.EmptyState
import br.com.inforprimetecnologia.vaidebuzao.ui.components.ListRow
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.state.Endpoint
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    vm: TransitViewModel,
    settings: AppSettings?,
    onOpenStop: (Int) -> Unit,
    onOpenLine: (String) -> Unit,
    onPlanTo: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val favorites by vm.favorites.collectAsState()
    val history by vm.history.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { VdbTopBar("Favoritos") }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Salvos") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Recentes") })
            }
            if (tab == 0) {
                if (favorites.isEmpty()) {
                    EmptyState(
                        "Nada salvo ainda",
                        "Toque na estrela de um ponto ou de uma linha para guardar aqui. Fica só no seu celular.",
                    )
                } else {
                    LazyColumn {
                        items(favorites, key = { it.id }) { f ->
                            when (f.type) {
                                FavoriteType.PONTO -> ListRow(f.title, subtitle = "Ponto de ônibus", icon = Icons.Filled.DirectionsBus, iconTint = VdbTheme.colors.stop) {
                                    f.ref.toIntOrNull()?.let(onOpenStop)
                                }
                                FavoriteType.LINHA -> ListRow(f.title, subtitle = "Linha", icon = Icons.Filled.Star, iconTint = MaterialTheme.colorScheme.secondary) {
                                    onOpenLine(f.ref)
                                }
                                FavoriteType.LUGAR -> ListRow(f.title, subtitle = "Lugar", icon = Icons.Filled.Place) {
                                    if (f.lat != null && f.lon != null) {
                                        vm.setDestination(Endpoint(f.title, GeoPoint(f.lat, f.lon)))
                                        vm.setOrigin(vm.myLocationEndpoint())
                                        vm.plan()
                                        onPlanTo()
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                when {
                    settings?.historyEnabled != true -> EmptyState(
                        "Histórico desativado",
                        "Por privacidade, o histórico de buscas só é salvo se você permitir. Você pode ativar em Ajustes › Privacidade.",
                        actionText = "Abrir privacidade",
                        onAction = onOpenPrivacy,
                    )
                    history.isEmpty() -> EmptyState("Sem buscas recentes", "Seus destinos buscados aparecem aqui.")
                    else -> LazyColumn {
                        item {
                            TextButton(onClick = { vm.clearHistory() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Limpar histórico") }
                        }
                        items(history, key = { it.id }) { h ->
                            ListRow(h.title, subtitle = h.createdAt.take(10).split("-").reversed().joinToString("/"), icon = Icons.Filled.History, iconTint = MaterialTheme.colorScheme.onSurfaceVariant) {
                                vm.setDestination(Endpoint(h.title, GeoPoint(h.lat, h.lon)))
                                vm.setOrigin(vm.myLocationEndpoint())
                                vm.plan()
                                onPlanTo()
                            }
                        }
                    }
                }
            }
        }
    }
}
