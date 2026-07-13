package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * routeFor answers "which link is this peer currently on" for the BLE/internet
 * badge: mesh preferred while fresh, otherwise the most recently heard link,
 * ties to mesh. A peer only ever heard on one link always reads as that link.
 */
class TransportRouterTest {

    @Test
    fun peerHeardOnMeshRecentlyRoutesMesh() {
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        assertEquals(Route.MESH, router.routeFor("bob", nowMs = 20_999L)) // 19.999 s ago
    }

    @Test
    fun peerOnlyEverOnMeshStaysMeshEvenWhenStale() {
        // Staleness is shown by the freshness dot, not by pretending a
        // mesh-only peer is suddenly reachable over the internet.
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        assertEquals(Route.MESH, router.routeFor("bob", nowMs = 999_999L))
    }

    @Test
    fun peerOnlyOnServerRoutesServer() {
        val router = TransportRouter()
        router.noteServerSeen("cloud", nowMs = 1_000L)
        assertEquals(Route.SERVER, router.routeFor("cloud", nowMs = 2_000L))
    }

    @Test
    fun dualHomedPrefersMeshWhileMeshFresh() {
        val router = TransportRouter()
        router.noteServerSeen("bob", nowMs = 5_000L)
        router.noteMeshSeen("bob", nowMs = 1_000L)   // older, but within 20 s window
        assertEquals(Route.MESH, router.routeFor("bob", nowMs = 6_000L))
    }

    @Test
    fun dualHomedFallsToServerWhenMeshGoesStale() {
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        router.noteServerSeen("bob", nowMs = 30_000L)   // still arriving over the relay
        assertEquals(Route.SERVER, router.routeFor("bob", nowMs = 31_000L))
    }

    @Test
    fun noteSeenDispatchesByRoute() {
        val router = TransportRouter()
        router.noteSeen("bob", Route.SERVER, nowMs = 1_000L)
        assertEquals(Route.SERVER, router.routeFor("bob", nowMs = 2_000L))
        router.noteSeen("bob", Route.MESH, nowMs = 3_000L)
        assertEquals(Route.MESH, router.routeFor("bob", nowMs = 4_000L))
    }

    @Test
    fun decisionIsPerPeer() {
        val router = TransportRouter()
        router.noteMeshSeen("near", nowMs = 100_000L)
        router.noteServerSeen("far", nowMs = 100_000L)
        assertEquals(Route.MESH, router.routeFor("near", nowMs = 105_000L))
        assertEquals(Route.SERVER, router.routeFor("far", nowMs = 105_000L))
    }
}
