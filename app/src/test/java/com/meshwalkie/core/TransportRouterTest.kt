package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportRouterTest {

    @Test
    fun peerHeardOnMeshRecentlyRoutesMesh() {
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        assertEquals(Route.MESH, router.routeFor("bob", nowMs = 20_999L)) // 19.999 s ago
    }

    @Test
    fun peerSilentOnMeshOver20sRoutesServer() {
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        assertEquals(Route.SERVER, router.routeFor("bob", nowMs = 21_001L)) // 20.001 s ago
    }

    @Test
    fun unknownPeerRoutesServer() {
        assertEquals(Route.SERVER, TransportRouter().routeFor("ghost", nowMs = 0L))
    }

    @Test
    fun decisionIsPerPeer() {
        val router = TransportRouter()
        router.noteMeshSeen("near", nowMs = 100_000L)
        router.noteMeshSeen("far", nowMs = 10_000L)
        assertEquals(Route.MESH, router.routeFor("near", nowMs = 105_000L))
        assertEquals(Route.SERVER, router.routeFor("far", nowMs = 105_000L))
    }
}
