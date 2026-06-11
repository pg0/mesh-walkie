package com.meshwalkie.core

/** Freshness dot: green <30 s, yellow <2 min, red stale. */
enum class Freshness { FRESH, AGING, STALE }

/** What the UI renders per peer. */
data class PeerView(
    val id: String,
    val name: String,
    val distanceMeters: Double,
    val bearingDeg: Double,
    val freshness: Freshness
)

/**
 * Lightweight roster entry: every known peer, shown even without any GPS fix
 * (on either side). hasPosition tells the UI whether a distance/arrow exists.
 */
data class PeerRosterEntry(
    val id: String,
    val name: String,
    val freshness: Freshness,
    val hasPosition: Boolean
)

/**
 * Who exists, where, how fresh. Fed by delivered packets,
 * queried by the UI with my current location.
 */
class PeerRegistry {
    private data class PeerState(
        var name: String? = null,
        var lat: Double? = null,
        var lon: Double? = null,
        var positionTimestampMs: Long = 0L,
        var lastSeenMs: Long = 0L
    )

    private val peers = LinkedHashMap<String, PeerState>()

    companion object {
        const val FRESH_MS = 30_000L
        const val AGING_MS = 120_000L
    }

    @Synchronized
    fun onPacket(packet: Packet, receivedAtMs: Long) {
        val state = peers.getOrPut(packet.originId) { PeerState() }
        state.lastSeenMs = maxOf(state.lastSeenMs, receivedAtMs)
        when (packet) {
            is Packet.Position -> if (packet.timestampMs >= state.positionTimestampMs) {
                state.lat = packet.lat
                state.lon = packet.lon
                state.positionTimestampMs = packet.timestampMs
            }
            is Packet.Presence -> state.name = packet.name
            is Packet.Voice -> Unit // lastSeen already refreshed
        }
    }

    /** Peers with a known position, nearest first. */
    @Synchronized
    fun snapshot(myLat: Double, myLon: Double, nowMs: Long): List<PeerView> =
        peers.mapNotNull { (id, s) ->
            val lat = s.lat ?: return@mapNotNull null
            val lon = s.lon ?: return@mapNotNull null
            val age = nowMs - s.lastSeenMs
            PeerView(
                id = id,
                name = s.name ?: id,
                distanceMeters = GeoMath.distanceMeters(myLat, myLon, lat, lon),
                bearingDeg = GeoMath.bearingDegrees(myLat, myLon, lat, lon),
                freshness = freshnessOf(age)
            )
        }.sortedBy { it.distanceMeters }

    /**
     * Every known peer, with or without a position - so the UI can show who is
     * connected even before any GPS fix exists on either device.
     */
    @Synchronized
    fun roster(nowMs: Long): List<PeerRosterEntry> =
        peers.map { (id, s) ->
            PeerRosterEntry(
                id = id,
                name = s.name ?: id,
                freshness = freshnessOf(nowMs - s.lastSeenMs),
                hasPosition = s.lat != null && s.lon != null
            )
        }.sortedBy { it.name }

    private fun freshnessOf(age: Long): Freshness = when {
        age < FRESH_MS -> Freshness.FRESH
        age < AGING_MS -> Freshness.AGING
        else -> Freshness.STALE
    }
}
