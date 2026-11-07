package br.com.inforprimetecnologia.vaidebuzao.ui.map

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** Pedido de movimento de câmera. Mude o [id] para repetir o mesmo movimento. */
data class CameraRequest(
    val id: Int,
    val center: GeoPoint? = null,
    val zoom: Double = 15.5,
    val bounds: List<GeoPoint>? = null,
)

/** Uma linha desenhada no mapa: trajeto de ônibus (sólido) ou caminhada (tracejado). */
data class MapPath(val points: List<GeoPoint>, val colorHex: String, val walking: Boolean = false)

/** Marcador simples (círculo colorido). */
data class MapMarker(val point: GeoPoint, val colorHex: String)

object MapStyles {
    // OpenFreeMap: gratuito, sem chave, sem cadastro. Dados © OpenMapTiles © colaboradores do OpenStreetMap.
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
    val CUIABA = GeoPoint(-15.6014, -56.0979)
}

private const val SRC_STOPS = "vdb-stops"
private const val SRC_RIDE = "vdb-ride"
private const val SRC_WALK = "vdb-walk"
private const val SRC_MARKERS = "vdb-markers"
private const val SRC_USER = "vdb-user"
private const val LAYER_STOPS = "vdb-stops-layer"

@Composable
fun VdbMap(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    stopsGeoJson: String? = null,
    stopColorHex: String = "#FF8A00",
    stopStrokeHex: String = "#FFFFFF",
    userLocation: GeoPoint? = null,
    userColorHex: String = "#1E5BF0",
    paths: List<MapPath> = emptyList(),
    markers: List<MapMarker> = emptyList(),
    camera: CameraRequest? = null,
    bottomPaddingPx: Int = 0,
    onStopClick: ((Int) -> Unit)? = null,
    onLongPress: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val onStopClickState = rememberUpdatedState(onStopClick)
    val onLongPressState = rememberUpdatedState(onLongPress)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(MapStyles.CUIABA.lat, MapStyles.CUIABA.lon))
                .zoom(12.0)
                .build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.addOnMapClickListener { latLng ->
                val cb = onStopClickState.value ?: return@addOnMapClickListener false
                val screen: PointF = m.projection.toScreenLocation(latLng)
                val r = 24f
                val features = m.queryRenderedFeatures(android.graphics.RectF(screen.x - r, screen.y - r, screen.x + r, screen.y + r), LAYER_STOPS)
                val id = features.firstOrNull()?.getNumberProperty("id")?.toInt()
                if (id != null) {
                    cb(id)
                    true
                } else {
                    false
                }
            }
            m.addOnMapLongClickListener { latLng ->
                val cb = onLongPressState.value ?: return@addOnMapLongClickListener false
                cb(GeoPoint(latLng.latitude, latLng.longitude))
                true
            }
            map = m
        }
    }

    // Estilo claro/escuro. Se o estilo escuro falhar, cai para o claro.
    LaunchedEffect(map, darkTheme) {
        val m = map ?: return@LaunchedEffect
        style = null
        var fellBack = false
        mapView.addOnDidFailLoadingMapListener {
            if (darkTheme && !fellBack) {
                fellBack = true
                m.setStyle(Style.Builder().fromUri(MapStyles.LIGHT)) { s -> setupLayers(s, stopColorHex, stopStrokeHex, userColorHex); style = s }
            }
        }
        m.setStyle(Style.Builder().fromUri(if (darkTheme) MapStyles.DARK else MapStyles.LIGHT)) { s ->
            setupLayers(s, stopColorHex, stopStrokeHex, userColorHex)
            style = s
        }
    }

    LaunchedEffect(style, stopsGeoJson) {
        style?.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(stopsGeoJson ?: MapGeoJson.EMPTY)
    }
    LaunchedEffect(style, paths) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_RIDE)?.setGeoJson(MapGeoJson.lines(paths.filter { !it.walking }.map { it.points to it.colorHex }))
        s.getSourceAs<GeoJsonSource>(SRC_WALK)?.setGeoJson(MapGeoJson.lines(paths.filter { it.walking }.map { it.points to it.colorHex }))
    }
    LaunchedEffect(style, markers) {
        style?.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(MapGeoJson.points(markers.map { it.point to it.colorHex }))
    }
    LaunchedEffect(style, userLocation) {
        style?.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(
            userLocation?.let { MapGeoJson.points(listOf(it to userColorHex)) } ?: MapGeoJson.EMPTY,
        )
    }
    LaunchedEffect(map, camera, bottomPaddingPx) {
        val m = map ?: return@LaunchedEffect
        val req = camera ?: return@LaunchedEffect
        m.setPadding(0, 0, 0, bottomPaddingPx)
        val pts = req.bounds?.distinct().orEmpty()
        if (pts.size >= 2) {
            val b = LatLngBounds.Builder().apply { pts.forEach { include(LatLng(it.lat, it.lon)) } }.build()
            val pad = with(density) { 56f * this.density }.toInt()
            runCatching { m.animateCamera(CameraUpdateFactory.newLatLngBounds(b, pad), 600) }
        } else {
            val c = req.center ?: pts.firstOrNull() ?: return@LaunchedEffect
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), req.zoom), 600)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun setupLayers(s: Style, stopColor: String, stopStroke: String, userColor: String) {
    listOf(SRC_STOPS, SRC_RIDE, SRC_WALK, SRC_MARKERS, SRC_USER).forEach { id ->
        if (s.getSource(id) == null) s.addSource(GeoJsonSource(id, MapGeoJson.EMPTY))
    }
    s.addLayer(
        LineLayer("vdb-ride-casing", SRC_RIDE).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    s.addLayer(
        LineLayer("vdb-ride", SRC_RIDE).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    s.addLayer(
        LineLayer("vdb-walk", SRC_WALK).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineDasharray(arrayOf(0.5f, 1.8f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    s.addLayer(
        CircleLayer(LAYER_STOPS, SRC_STOPS).withProperties(
            PropertyFactory.circleColor(
                Expression.switchCase(Expression.toBool(Expression.get("served")), Expression.color(android.graphics.Color.parseColor(stopColor)), Expression.color(android.graphics.Color.parseColor("#8A93B0"))),
            ),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(12, 2f),
                    Expression.stop(15, 5f),
                    Expression.stop(18, 9f),
                ),
            ),
            PropertyFactory.circleStrokeColor(stopStroke),
            PropertyFactory.circleStrokeWidth(1.5f),
        ).apply { minZoom = 12f },
    )
    s.addLayer(
        CircleLayer("vdb-markers", SRC_MARKERS).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    s.addLayer(
        CircleLayer("vdb-user-halo", SRC_USER).withProperties(
            PropertyFactory.circleColor(userColor),
            PropertyFactory.circleRadius(20f),
            PropertyFactory.circleOpacity(0.18f),
        ),
    )
    s.addLayer(
        CircleLayer("vdb-user", SRC_USER).withProperties(
            PropertyFactory.circleColor(userColor),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}
