package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.transit.SearchHit
import br.com.inforprimetecnologia.vaidebuzao.data.FavoriteType
import br.com.inforprimetecnologia.vaidebuzao.ui.components.InfoBanner
import br.com.inforprimetecnologia.vaidebuzao.ui.components.ListRow
import br.com.inforprimetecnologia.vaidebuzao.ui.components.SectionHeader
import br.com.inforprimetecnologia.vaidebuzao.ui.components.VdbTopBar
import br.com.inforprimetecnologia.vaidebuzao.ui.state.Endpoint
import br.com.inforprimetecnologia.vaidebuzao.ui.state.TransitViewModel
import br.com.inforprimetecnologia.vaidebuzao.ui.theme.VdbTheme

private enum class Field { ORIGIN, DEST }

fun kindIcon(kind: String): ImageVector = when (kind) {
    "Hospital", "Saúde" -> Icons.Filled.LocalHospital
    "Ensino" -> Icons.Filled.School
    "Terminal" -> Icons.Filled.DirectionsBus
    "Bairro", "Cidade", "Localidade" -> Icons.Filled.Apartment
    else -> Icons.Filled.Place
}

@Composable
fun SearchScreen(vm: TransitViewModel, onBack: () -> Unit, onPlanned: () -> Unit) {
    val trip by vm.trip.collectAsState()
    val search by vm.search.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val history by vm.history.collectAsState()
    val reference by vm.reference.collectAsState()
    var field by rememberSaveable { mutableStateOf(Field.DEST) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (trip.origin == null) vm.myLocationEndpoint()?.let { vm.setOrigin(it) }
        vm.clearSearch()
        runCatching { focus.requestFocus() }
    }

    fun choose(e: Endpoint) {
        if (field == Field.ORIGIN) {
            vm.setOrigin(e)
            vm.clearSearch()
            if (trip.destination == null) field = Field.DEST else {
                vm.plan()
                onPlanned()
            }
        } else {
            vm.setDestination(e)
            vm.clearSearch()
            val origin = vm.trip.value.origin ?: vm.myLocationEndpoint()
            if (origin == null) {
                field = Field.ORIGIN
            } else {
                vm.setOrigin(origin)
                vm.plan()
                onPlanned()
            }
        }
    }

    Scaffold(topBar = { VdbTopBar("Para onde vamos?", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 2.dp,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EndpointField(
                            label = "Origem",
                            icon = Icons.Filled.TripOrigin,
                            iconTint = MaterialTheme.colorScheme.primary,
                            active = field == Field.ORIGIN,
                            value = trip.origin?.name,
                            query = search.query,
                            onActivate = { field = Field.ORIGIN; vm.clearSearch() },
                            onQuery = vm::onQueryChange,
                            focus = if (field == Field.ORIGIN) focus else null,
                        )
                        EndpointField(
                            label = "Destino",
                            icon = Icons.Filled.Place,
                            iconTint = VdbTheme.colors.stop,
                            active = field == Field.DEST,
                            value = trip.destination?.name,
                            query = search.query,
                            onActivate = { field = Field.DEST; vm.clearSearch() },
                            onQuery = vm::onQueryChange,
                            focus = if (field == Field.DEST) focus else null,
                        )
                    }
                    IconButton(onClick = { vm.swap() }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Inverter origem e destino")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                if (search.query.isBlank()) {
                    if (field == Field.ORIGIN || trip.origin?.isMyLocation != true) {
                        vm.myLocationEndpoint()?.let { me ->
                            item {
                                ListRow(
                                    me.name,
                                    subtitle = "Usar como ${if (field == Field.ORIGIN) "origem" else "destino"}",
                                    icon = Icons.Filled.MyLocation,
                                    onClick = { choose(me) },
                                )
                            }
                        }
                    }
                    val favPlaces = favorites.filter { it.lat != null && it.lon != null && it.type != FavoriteType.LINHA }
                    if (favPlaces.isNotEmpty()) {
                        item { SectionHeader("Favoritos", Modifier.padding(horizontal = 20.dp)) }
                        items(favPlaces, key = { "f${it.id}" }) { f ->
                            ListRow(f.title, subtitle = if (f.type == FavoriteType.PONTO) "Ponto de ônibus" else "Lugar", icon = Icons.Filled.Star, iconTint = MaterialTheme.colorScheme.secondary) {
                                choose(Endpoint(f.title, GeoPoint(f.lat!!, f.lon!!)))
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        item { SectionHeader("Buscas recentes", Modifier.padding(horizontal = 20.dp)) }
                        items(history, key = { "h${it.id}" }) { h ->
                            ListRow(h.title, icon = Icons.Filled.History, iconTint = MaterialTheme.colorScheme.onSurfaceVariant) {
                                choose(Endpoint(h.title, GeoPoint(h.lat, h.lon)))
                            }
                        }
                    }
                    if (favPlaces.isEmpty() && history.isEmpty()) {
                        item {
                            Text(
                                "Digite um bairro, endereço, terminal, hospital, faculdade ou shopping de Cuiabá e Várzea Grande.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                } else {
                    if (search.local.isNotEmpty()) {
                        item { SectionHeader("No app (funciona sem internet)", Modifier.padding(horizontal = 20.dp)) }
                        items(search.local) { hit ->
                            when (hit) {
                                is SearchHit.PlaceHit -> ListRow(hit.place.name, subtitle = hit.place.kind, icon = kindIcon(hit.place.kind)) {
                                    choose(Endpoint(hit.place.name, hit.place.point))
                                }
                                is SearchHit.StopHit -> ListRow(hit.stop.displayName, subtitle = "Ponto de ônibus", icon = Icons.Filled.DirectionsBus, iconTint = VdbTheme.colors.stop) {
                                    choose(Endpoint(hit.stop.displayName, hit.stop.point))
                                }
                            }
                        }
                    }
                    item { SectionHeader("Endereços (OpenStreetMap)", Modifier.padding(horizontal = 20.dp)) }
                    when {
                        search.remoteLoading -> item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                        search.remoteError -> item {
                            InfoBanner(
                                "Sem conexão para buscar endereços. A busca offline acima continua funcionando.",
                                icon = Icons.Filled.CloudOff,
                                warning = true,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        search.remote.isEmpty() && search.query.trim().length >= 3 -> item {
                            Text("Nada encontrado.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                        }
                        else -> items(search.remote) { r ->
                            ListRow(r.title, subtitle = r.subtitle, icon = Icons.Filled.Place) { choose(Endpoint(r.title, r.point)) }
                        }
                    }
                    item {
                        Text(
                            "Ao buscar endereços, o texto digitado é enviado ao serviço Nominatim (OpenStreetMap). Sua localização não é enviada.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointField(
    label: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    active: Boolean,
    value: String?,
    query: String,
    onActivate: () -> Unit,
    onQuery: (String) -> Unit,
    focus: FocusRequester?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        if (active) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text(label) },
                placeholder = { Text(value ?: "Buscar lugar ou endereço", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Limpar") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        } else {
            Surface(
                onClick = onActivate,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value ?: "Toque para escolher", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    HorizontalDivider(color = androidx.compose.ui.graphics.Color.Transparent)
}
