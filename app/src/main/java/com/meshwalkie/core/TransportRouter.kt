package com.meshwalkie.core

enum class Route { MESH, SERVER }

/**
 * Mesh always preferred, per-peer: heard on mesh within [meshTimeoutMs] -> MESH,
 * otherwise SERVER. Phase 1 has no ServerTransport; callers treat SERVER as
 * "unreachable for direct addressing" and still flood the mesh.
 */
class TransportRouter(private val meshTimeoutMs: Long = 20_000L) {
    private val meshLastSeen = HashMap<String, Long>()

    @Synchronized
    fun noteMeshSeen(peerId: String, nowMs: Long) {
        meshLastSeen[peerId] = nowMs
    }

    @Synchronized
    fun routeFor(peerId: String, nowMs: Long): Route {
        val last = meshLastSeen[peerId] ?: return Route.SERVER
        return if (nowMs - last <= meshTimeoutMs) Route.MESH else Route.SERVER
    }
}
