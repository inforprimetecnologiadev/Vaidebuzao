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
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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
