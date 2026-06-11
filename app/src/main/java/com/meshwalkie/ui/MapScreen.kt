package com.meshwalkie.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.osmdroid.views.overlay.Polyline

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
    breadcrumbs: List<Pair<Double, Double>>,
    onSetTarget: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
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
            // breadcrumb trail
            if (breadcrumbs.size >= 2) {
                map.overlays.add(Polyline().apply {
                    setPoints(breadcrumbs.map { GeoPoint(it.first, it.second) })
                    outlinePaint.color = android.graphics.Color.argb(180, 79, 195, 247)
                    outlinePaint.strokeWidth = 6f
                })
            }
            // tap to set target
            map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    onSetTarget(p.latitude, p.longitude); return true
                }
                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            myLoc?.let { (lat, lon) ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(lat, lon); title = "You"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            peers.forEach { p ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(p.lat, p.lon)
                    title = "${p.name}  ${Display.formatDistance(p.distanceMeters)}"
                })
            }
            waypoints.forEach { w ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(w.lat, w.lon); title = "📍 ${w.label}"
                })
            }
            target?.let { (lat, lon) ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(lat, lon); title = "🎯 Target"
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
}
