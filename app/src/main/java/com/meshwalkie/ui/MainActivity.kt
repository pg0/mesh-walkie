package com.meshwalkie.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.MeshService
import com.meshwalkie.service.Settings
import com.meshwalkie.util.L

class MainActivity : ComponentActivity() {

    // Guards against starting the service twice (permission callback + onResume).
    private var serviceStarted = false

    // Set when permissions are granted while the activity is not yet resumed.
    // A foreground-service start in that window throws
    // ForegroundServiceStartNotAllowedException on API 31+, so we defer the
    // actual start to onResume.
    private var pendingStart = false

    // True between onResume and onPause. We cannot rely on
    // lifecycle.currentState here: AndroidX dispatches ON_RESUME AFTER the
    // activity's onResume() returns, so inside onResume the state still reads
    // STARTED. Being inside onResume already means the app is foreground-visible,
    // which is exactly when a foreground-service start is permitted.
    private var resumed = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                // The result callback can fire before the activity is resumed;
                // defer the FGS start instead of calling it here.
                pendingStart = true
                maybeStartMeshService()
            }
            // denied -> UI still renders; peer list stays empty until granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        setContent {
            val theme by Settings.theme.collectAsStateWithLifecycle()
            val colors = themeColorScheme(theme)
            // System bars follow the selected theme (background color + icon contrast).
            SideEffect {
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = themeIsLight(theme)
                controller.isAppearanceLightNavigationBars = themeIsLight(theme)
            }
            CompositionLocalProvider(LocalAppTheme provides theme) {
                MaterialTheme(
                    colorScheme = colors,
                    shapes = themeShapes(theme),
                    typography = themeTypography(theme)
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        var showSettings by rememberSaveable { mutableStateOf(false) }
                        if (showSettings) {
                            SettingsScreen(onBack = { showSettings = false })
                        } else {
                            PeerListScreen(
                                onOpenSettings = { showSettings = true },
                                onExit = {
                                    stopService(Intent(this@MainActivity, MeshService::class.java))
                                    finishAndRemoveTask()
                                }
                            )
                        }
                    }
                }
            }
        }
        ensurePermissionsThenStart()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (Settings.volumePtt.value && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event?.repeatCount == 0) com.meshwalkie.service.MeshBus.pttHandler?.invoke(true)
            return true   // consume so it doesn't change volume
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (Settings.volumePtt.value && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            com.meshwalkie.service.MeshBus.pttHandler?.invoke(false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Foreground-visible now: safe to start a foreground service.
        maybeStartMeshService()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
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
     * Starts the foreground service exactly once, and only while the activity is
     * resumed (foreground-visible). If called too early (e.g. from the
     * permission-result callback before onResume), it no-ops and onResume retries.
     */
    private fun maybeStartMeshService() {
        if (serviceStarted || !pendingStart || !resumed) return
        try {
            startForegroundService(Intent(this, MeshService::class.java))
            serviceStarted = true
            pendingStart = false
        } catch (e: Exception) {
            // Do not crash if the platform still refuses the start; it will be
            // retried on the next onResume.
            L.w(TAG, "startForegroundService failed, will retry on resume", e)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
