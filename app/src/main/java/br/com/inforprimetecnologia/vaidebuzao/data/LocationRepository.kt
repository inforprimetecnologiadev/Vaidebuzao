package br.com.inforprimetecnologia.vaidebuzao.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import br.com.inforprimetecnologia.vaidebuzao.core.geo.GeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.location.LocationListener
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Leitura do GPS com a precisão estimada (metros). */
data class LocationFix(val point: GeoPoint, val accuracyMeters: Float, val timeMillis: Long)

/**
 * Localização sob demanda (somente com o app aberto). Nada é gravado: a posição vive só na memória.
 * Usa o Fused Location Provider e, sem Google Play Services, o LocationManager do Android.
 */
class LocationRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val loc = withTimeoutOrNull(12_000) { fused() } ?: withTimeoutOrNull(12_000) { platform() }
        return loc?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /**
     * Atualizações contínuas (a cada ~3 s) para o "Gravar linha". Quem coleta deve parar quando o app sai da tela
     * (repeatOnLifecycle), para respeitar a regra "localização só com o app aberto".
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMillis: Long = 3000): Flow<LocationFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        fun emit(loc: Location) {
            trySend(LocationFix(GeoPoint(loc.latitude, loc.longitude), if (loc.hasAccuracy()) loc.accuracy else 999f, System.currentTimeMillis()))
        }
        var cleanup: () -> Unit = {}
        val fusedOk = try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { emit(it) }
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis / 2)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            cleanup = { client.removeLocationUpdates(callback) }
            true
        } catch (_: Exception) {
            false
        }
        if (!fusedOk) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { p -> lm != null && runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
            if (lm != null && provider != null) {
                // Implementação completa: em Android < 11 os outros métodos não têm implementação padrão.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = emit(location)
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit

                    @Deprecated("API antiga")
                    @Suppress("DEPRECATION")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                }
                lm.requestLocationUpdates(provider, intervalMillis, 0f, listener, Looper.getMainLooper())
                cleanup = { lm.removeUpdates(listener) }
            }
        }
        awaitClose { cleanup() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fused(): Location? = try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun platform(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return try {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull())
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
