package br.com.inforprimetecnologia.vaidebuzao.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Itinerary
import br.com.inforprimetecnologia.vaidebuzao.core.routing.Router
import br.com.inforprimetecnologia.vaidebuzao.core.transit.NearbyStop
import br.com.inforprimetecnologia.vaidebuzao.core.transit.SearchHit
import br.com.inforprimetecnologia.vaidebuzao.core.transit.TransitNetwork
import br.com.inforprimetecnologia.vaidebuzao.data.AppContainer
import br.com.inforprimetecnologia.vaidebuzao.data.Favorite
import br.com.inforprimetecnologia.vaidebuzao.data.FavoriteType
import br.com.inforprimetecnologia.vaidebuzao.data.HistoryItem
import br.com.inforprimetecnologia.vaidebuzao.data.RemotePlace
import br.com.inforprimetecnologia.vaidebuzao.ui.map.MapGeoJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LocationStatus { UNKNOWN, NO_PERMISSION, DISABLED, SEARCHING, OK, FAILED }

/** Origem ou destino de uma viagem. */
data class Endpoint(val name: String, val point: GeoPoint, val isMyLocation: Boolean = false)

data class SearchState(
    val query: String = "",
    val local: List<SearchHit> = emptyList(),
    val remote: List<RemotePlace> = emptyList(),
    val remoteLoading: Boolean = false,
    val remoteError: Boolean = false,
)

data class TripState(
    val origin: Endpoint? = null,
    val destination: Endpoint? = null,
    val loading: Boolean = false,
    val results: List<Itinerary>? = null,
)

class TransitViewModel(private val c: AppContainer) : ViewModel() {

    private val _network = MutableStateFlow<TransitNetwork?>(null)
    val network: StateFlow<TransitNetwork?> = _network.asStateFlow()

    private var router: Router? = null

    /** GeoJSON de todos os pontos (calculado uma vez). */
    private val _stopsGeoJson = MutableStateFlow<String?>(null)
    val stopsGeoJson: StateFlow<String?> = _stopsGeoJson.asStateFlow()

    private val _location = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = _location.asStateFlow()

    private val _locationStatus = MutableStateFlow(LocationStatus.UNKNOWN)
    val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()

    /** Ponto escolhido à mão no mapa (toque longo), quando não há GPS. */
    private val _manualOrigin = MutableStateFlow<GeoPoint?>(null)
    val manualOrigin: StateFlow<GeoPoint?> = _manualOrigin.asStateFlow()

    val reference: StateFlow<GeoPoint?> = combine(_manualOrigin, _location) { m, l -> m ?: l }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nearby: StateFlow<List<NearbyStop>?> = combine(_network, reference) { net, ref ->
        if (net == null) null else if (ref == null) emptyList() else net.nearestStops(ref, limit = 10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var remoteJob: Job? = null

    private val _trip = MutableStateFlow(TripState())
    val trip: StateFlow<TripState> = _trip.asStateFlow()

    val favorites: StateFlow<List<Favorite>> = c.favorites.favorites
    val history: StateFlow<List<HistoryItem>> = c.favorites.history

    init {
        viewModelScope.launch {
            val net = c.transit.network()
            router = withContext(Dispatchers.Default) { Router(net) }
            _stopsGeoJson.value = withContext(Dispatchers.Default) { MapGeoJson.stops(net) }
            _network.value = net
        }
    }

    fun refreshLocation() = viewModelScope.launch {
        if (!c.location.hasPermission()) {
            _locationStatus.value = LocationStatus.NO_PERMISSION
            return@launch
        }
        if (!c.location.isLocationEnabled()) {
            _locationStatus.value = LocationStatus.DISABLED
            return@launch
        }
        _locationStatus.value = LocationStatus.SEARCHING
        val p = c.location.currentLocation()
        if (p != null) {
            _location.value = p
            _manualOrigin.value = null
            _locationStatus.value = LocationStatus.OK
        } else {
            _locationStatus.value = LocationStatus.FAILED
        }
    }

    fun setManualOrigin(point: GeoPoint?) {
        _manualOrigin.value = point
    }

    // ------------------------------------------------------------------ busca

    @OptIn(FlowPreview::class)
    fun onQueryChange(query: String) {
        val net = _network.value ?: TransitNetwork.EMPTY
        val local = net.searchPlaces(query, near = reference.value)
        _search.value = _search.value.copy(query = query, local = local, remote = emptyList(), remoteError = false, remoteLoading = false)
        remoteJob?.cancel()
        if (query.trim().length >= 3) {
            remoteJob = viewModelScope.launch {
                delay(700) // debounce: só busca online depois que a pessoa para de digitar
                _search.value = _search.value.copy(remoteLoading = true)
                val result = c.geocoding.search(query)
                if (_search.value.query == query) {
                    _search.value = _search.value.copy(
                        remote = result.getOrDefault(emptyList()),
                        remoteError = result.isFailure,
                        remoteLoading = false,
                    )
                }
            }
        }
    }

    fun clearSearch() {
        remoteJob?.cancel()
        _search.value = SearchState()
    }

    // ------------------------------------------------------------------ viagem

    fun myLocationEndpoint(): Endpoint? = reference.value?.let {
        Endpoint(if (_manualOrigin.value != null) "Local escolhido no mapa" else "Minha localização", it, isMyLocation = true)
    }

    fun setOrigin(e: Endpoint?) {
        _trip.value = _trip.value.copy(origin = e, results = null)
    }

    fun setDestination(e: Endpoint?) {
        _trip.value = _trip.value.copy(destination = e, results = null)
    }

    fun swap() {
        val t = _trip.value
        _trip.value = t.copy(origin = t.destination, destination = t.origin, results = null)
    }

    /** Calcula as opções de rota (offline, no próprio aparelho). */
    fun plan() = viewModelScope.launch {
        val t = _trip.value
        val origin = t.origin ?: myLocationEndpoint() ?: return@launch
        val dest = t.destination ?: return@launch
        _trip.value = t.copy(origin = origin, loading = true, results = null)
        if (!dest.isMyLocation && c.settings.settings.first().historyEnabled) {
            c.favorites.addHistory(dest.name, dest.point.lat, dest.point.lon)
        }
        val r = router ?: Router(c.transit.network()).also { router = it }
        val results = withContext(Dispatchers.Default) { r.route(origin.point, origin.name, dest.point, dest.name) }
        _trip.value = _trip.value.copy(loading = false, results = results)
    }

    // ------------------------------------------------------------------ favoritos

    fun toggleFavorite(type: FavoriteType, ref: String, title: String, point: GeoPoint?) = viewModelScope.launch {
        c.favorites.toggle(type, ref, title, point?.lat, point?.lon)
    }

    fun isFavorite(type: FavoriteType, ref: String) = favorites.value.any { it.type == type && it.ref == ref }

    fun clearHistory() = viewModelScope.launch { c.favorites.clearHistory() }
}
