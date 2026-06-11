package com.meshwalkie.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.meshwalkie.core.Display
import com.meshwalkie.core.PeerView
import com.meshwalkie.core.WaypointView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Real map via osmdroid (OpenStreetMap, no API key, tiles cache for offline).
 * Plots me, peers, waypoints, and a tap-set navigation target. Tap anywhere to
 * set the target.
 */
@Composable
fun MapScreen(
    peers: List<PeerView>,
    myLoc: Pair<Double, Double>?,
    waypoints: List<WaypointView>,
    target: Pair<Double, Double>?,
    onSetTarget: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val centered = remember { booleanArrayOf(false) }
    val mapHolder = remember { arrayOfNulls<MapView>(1) }

    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            Configuration.getInstance().load(
                c, c.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = c.packageName
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
            }.also { mapHolder[0] = it }
        },
        update = { map ->
            map.overlays.clear()
            // tap to set target
            map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    onSetTarget(p.latitude, p.longitude); return true
                }
                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            fun Marker.styleLabel(bg: Int) {
                setTextLabelFontSize(40)
                setTextLabelForegroundColor(android.graphics.Color.WHITE)
                setTextLabelBackgroundColor(bg)
            }
            myLoc?.let { (lat, lon) ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(lat, lon); title = "You"
                    styleLabel(android.graphics.Color.argb(220, 21, 101, 192))   // blue
                    setTextIcon("YOU")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            peers.forEach { p ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(p.lat, p.lon)
                    title = "${p.name}  ${Display.formatDistance(p.distanceMeters)}"
                    styleLabel(android.graphics.Color.argb(220, 46, 125, 50))    // green
                    setTextIcon(p.name)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            waypoints.forEach { w ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(w.lat, w.lon); title = "Waypoint: ${w.label}"
                    styleLabel(android.graphics.Color.argb(220, 255, 160, 0))    // amber
                    setTextIcon("📍 ${w.label}")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            target?.let { (lat, lon) ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(lat, lon); title = "Target"
                    styleLabel(android.graphics.Color.argb(220, 198, 40, 40))    // red
                    setTextIcon("🎯 TARGET")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            if (!centered[0]) {
                val c0 = myLoc?.let { GeoPoint(it.first, it.second) }
                    ?: peers.firstOrNull()?.let { GeoPoint(it.lat, it.lon) }
                if (c0 != null) { map.controller.setCenter(c0); centered[0] = true }
            }
            map.invalidate()
        }
    )
        myLoc?.let { (lat, lon) ->
            Button(
                onClick = { mapHolder[0]?.controller?.animateTo(GeoPoint(lat, lon)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            ) { Text("◎ Me") }
        }
    }
}
