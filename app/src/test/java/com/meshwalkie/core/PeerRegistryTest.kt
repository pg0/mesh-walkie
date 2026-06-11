package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRegistryTest {

    private val now = 1_000_000L

    private fun bobAtMunich(ts: Long) =
        Packet.Position("bob", 1, 4, ts, lat = 48.137, lon = 11.575, headingDeg = 90f)

    @Test
    fun positionPacketCreatesPeerWithDistanceAndBearing() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        val views = reg.snapshot(myLat = 52.52, myLon = 13.405, nowMs = now)
        assertEquals(1, views.size)
        val bob = views[0]
        assertEquals("bob", bob.id)
        assertEquals(504337.9, bob.distanceMeters, 500.0)   // Berlin -> Munich
        assertEquals(195.634, bob.bearingDeg, 0.01)          // SSW
        assertEquals(Freshness.FRESH, bob.freshness)
    }

    @Test
    fun presencePacketSetsName() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(Packet.Presence("bob", 2, 4, now, name = "Bob K", batteryPct = 80), receivedAtMs = now)
        assertEquals("Bob K", reg.snapshot(52.52, 13.405, now)[0].name)
    }

    @Test
    fun nameDefaultsToIdUntilPresenceArrives() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        assertEquals("bob", reg.snapshot(52.52, 13.405, now)[0].name)
    }

    @Test
    fun freshnessAges() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        assertEquals(Freshness.FRESH, reg.snapshot(52.52, 13.405, now + 29_999)[0].freshness)
        assertEquals(Freshness.AGING, reg.snapshot(52.52, 13.405, now + 30_000)[0].freshness)
        assertEquals(Freshness.AGING, reg.snapshot(52.52, 13.405, now + 119_999)[0].freshness)
        assertEquals(Freshness.STALE, reg.snapshot(52.52, 13.405, now + 120_000)[0].freshness)
    }

    @Test
    fun newerPositionWins() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(
            Packet.Position("bob", 5, 4, now + 5000, lat = 52.62, lon = 13.205, headingDeg = 0f),
            receivedAtMs = now + 5000
        )
        val bob = reg.snapshot(52.52, 13.405, now + 5000)[0]
        assertEquals(17502.7, bob.distanceMeters, 50.0)  // 52.52,13.405 -> 52.62,13.205
        assertEquals(309.522, bob.bearingDeg, 0.01)       // NW
    }

    @Test
    fun voicePacketsRefreshLastSeenButNotPosition() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(
            Packet.Voice("bob", 9, 4, now + 100_000, 1, 0, false, byteArrayOf(1)),
            receivedAtMs = now + 100_000
        )
        val bob = reg.snapshot(52.52, 13.405, now + 100_000)[0]
        assertEquals(Freshness.FRESH, bob.freshness)
        assertEquals(504337.9, bob.distanceMeters, 500.0) // position unchanged
    }

    @Test
    fun snapshotSortedByDistance() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(
            Packet.Position("near", 1, 4, now, lat = 52.62, lon = 13.205, headingDeg = 0f),
            receivedAtMs = now
        )
        val views = reg.snapshot(52.52, 13.405, now)
        assertEquals(listOf("near", "bob"), views.map { it.id })
        assertTrue(views[0].distanceMeters < views[1].distanceMeters)
    }
}
