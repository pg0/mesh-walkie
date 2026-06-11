package com.meshwalkie.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** FusedLocationProviderClient wrapper. High accuracy, each fix -> POSITION packet. */
class LocationSource(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    /** Caller (MeshService) holds ACCESS_FINE_LOCATION before calling. */
    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long = 4_000L, onFix: (Location) -> Unit) {
        // Seed immediately with the last known fix so a phone that located
        // recently (via any app) shows position at once instead of waiting for
        // a fresh fix. Helps the device with weaker/slower GPS.
        client.lastLocation.addOnSuccessListener { last -> last?.let(onFix) }

        // HIGH_ACCURACY drives the GPS chip harder than balanced power, which
        // matters indoors / near windows where a fix is otherwise never produced.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, intervalMs
        ).setMinUpdateIntervalMillis(2_000L).build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(onFix)
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
