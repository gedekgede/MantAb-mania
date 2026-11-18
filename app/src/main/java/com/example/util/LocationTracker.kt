package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long
)

object LocationTracker {

    fun hasLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentFix(context: Context): LocationFix? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        cont.resume(
                            LocationFix(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                accuracyMeters = loc.accuracy,
                                timestampMillis = loc.time
                            )
                        )
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last: Location? ->
                                cont.resume(
                                    last?.let {
                                        LocationFix(it.latitude, it.longitude, it.accuracy, it.time)
                                    }
                                )
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    @SuppressLint("MissingPermission")
    fun locationUpdatesFlow(context: Context, intervalMs: Long = 8000L): Flow<LocationFix> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close()
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LocationFix(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy,
                        timestampMillis = loc.time
                    )
                )
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            String.format(Locale.US, "%.0f m", meters)
        } else {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        }
    }

    fun formatCoords(lat: Double, lng: Double): String {
        return String.format(Locale.US, "%.5f, %.5f", lat, lng)
    }

    fun openInGoogleMaps(context: Context, lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label)
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        runCatching { context.startActivity(intent) }
    }
}
