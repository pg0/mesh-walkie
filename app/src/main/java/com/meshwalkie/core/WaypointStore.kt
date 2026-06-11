package com.meshwalkie.core

/** A fixed dropped point as the UI renders it. distance/bearing need my fix. */
data class WaypointView(
    val id: String,
    val senderName: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Double,
    val bearingDeg: Double
)

/**
 * Holds dropped waypoints (rally points, "car here"). A waypoint is a peer that
 * never moves, so distance/bearing reuse GeoMath. Pure + testable.
 */
class WaypointStore {
    private data class WP(val senderName: String, val lat: Double, val lon: Double, val label: String)

    private val points = LinkedHashMap<String, WP>()

    @Synchronized
    fun add(id: String, senderName: String, lat: Double, lon: Double, label: String) {
        points[id] = WP(senderName, lat, lon, label)
    }

    @Synchronized
    fun remove(id: String) { points.remove(id) }

    @Synchronized
    fun snapshot(myLat: Double, myLon: Double): List<WaypointView> =
        points.map { (id, w) ->
            WaypointView(
                id = id,
                senderName = w.senderName,
                label = w.label,
                lat = w.lat,
                lon = w.lon,
                distanceMeters = GeoMath.distanceMeters(myLat, myLon, w.lat, w.lon),
                bearingDeg = GeoMath.bearingDegrees(myLat, myLon, w.lat, w.lon)
            )
        }.sortedBy { it.distanceMeters }
}
