package com.meshwalkie.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.meshwalkie.core.Display
import com.meshwalkie.core.PeerView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Real map view via osmdroid (OpenStreetMap). No API key. Tiles load over the
 * network now and osmdroid caches them, so a pre-browsed area still renders
 * offline in the field. Plots me + every peer with a known position.
 */
@Composable
fun MapScreen(peers: List<PeerView>, myLoc: Pair<Double, Double>?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // Center only once so GPS updates don't keep yanking the map back.
    val centered = remember { booleanArrayOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            Configuration.getInstance().load(
                c, c.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = c.packageName
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
            }
        },
        update = { map ->
            map.overlays.clear()
            myLoc?.let { (lat, lon) ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(lat, lon)
                    title = "You"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            peers.forEach { p ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(p.lat, p.lon)
                    title = "${p.name}  ${Display.formatDistance(p.distanceMeters)}"
                })
            }
            if (!centered[0]) {
                val c0 = myLoc?.let { GeoPoint(it.first, it.second) }
                    ?: peers.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
                if (c0 != null) {
                    map.controller.setCenter(c0)
                    centered[0] = true
                }
            }
            map.invalidate()
        }
    )
}
