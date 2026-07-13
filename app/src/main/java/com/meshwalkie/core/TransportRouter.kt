package com.meshwalkie.core

enum class Route { MESH, SERVER }

/**
 * Tracks, per peer, when it was last heard over the BLE mesh and over an
 * internet server, and answers "which link is this peer on right now".
 *
 * Mesh is preferred while fresh: a peer heard on mesh within [meshTimeoutMs]
 * reads as MESH even if it is also reachable over a server (local/direct beats
 * relayed). Otherwise the most recently heard link wins; ties fall to MESH.
 */
class TransportRouter(private val meshTimeoutMs: Long = 20_000L) {
    private val meshLastSeen = HashMap<String, Long>()
    private val serverLastSeen = HashMap<String, Long>()

    @Synchronized
    fun noteMeshSeen(peerId: String, nowMs: Long) {
        meshLastSeen[peerId] = nowMs
    }

    @Synchronized
    fun noteServerSeen(peerId: String, nowMs: Long) {
        serverLastSeen[peerId] = nowMs
    }

    /** Record a sighting on the given route (convenience for a tagged intake). */
    @Synchronized
    fun noteSeen(peerId: String, route: Route, nowMs: Long) {
        when (route) {
            Route.MESH -> meshLastSeen[peerId] = nowMs
            Route.SERVER -> serverLastSeen[peerId] = nowMs
        }
    }

    @Synchronized
    fun routeFor(peerId: String, nowMs: Long): Route {
        val mesh = meshLastSeen[peerId]
        val server = serverLastSeen[peerId]
        if (mesh != null && nowMs - mesh <= meshTimeoutMs) return Route.MESH
        if (server == null) return Route.MESH   // only ever heard on mesh
        if (mesh == null) return Route.SERVER    // only ever heard on server
        return if (mesh >= server) Route.MESH else Route.SERVER
    }
}
