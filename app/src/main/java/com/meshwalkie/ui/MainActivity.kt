package com.meshwalkie.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import com.meshwalkie.service.MeshService

class MainActivity : ComponentActivity() {

    // Guards against starting the service twice (permission callback + onResume).
    private var serviceStarted = false

    // Set when permissions are granted while the activity is not yet RESUMED.
    // A foreground-service start in that window throws
    // ForegroundServiceStartNotAllowedException on API 31+, so we defer the
    // actual start to onResume.
    private var pendingStart = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                // The result callback can fire before the activity is RESUMED;
                // defer the FGS start instead of calling it here.
                pendingStart = true
                maybeStartMeshService()
            }
            // denied -> UI still renders; peer list stays empty until granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PeerListScreen() } }
        ensurePermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        // Now at least RESUMED: safe to start a foreground service.
        maybeStartMeshService()
    }

    private fun ensurePermissionsThenStart() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = needed.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            pendingStart = true
            maybeStartMeshService()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    /**
     * Starts the foreground service exactly once, and only when the activity is
     * at least RESUMED. If called too early (e.g. from the permission-result
     * callback before onResume), it no-ops and onResume retries.
     */
    private fun maybeStartMeshService() {
        if (serviceStarted || !pendingStart) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        try {
            startForegroundService(Intent(this, MeshService::class.java))
            serviceStarted = true
            pendingStart = false
        } catch (e: Exception) {
            // Do not crash if the platform still refuses the start; it will be
            // retried on the next onResume.
            Log.w(TAG, "startForegroundService failed, will retry on resume", e)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
