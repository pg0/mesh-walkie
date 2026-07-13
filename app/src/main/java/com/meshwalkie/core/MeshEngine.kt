package com.meshwalkie.core

/**
 * Glues one Transport to the flood logic.
 * send(): mark own (kills echoes) + broadcast.
 * receive: decode -> flood decision -> deliver locally and/or re-broadcast ttl-1.
 */
class MeshEngine(
    private val transport: Transport,
    private val flood: FloodController = FloodController()
) {
    private var onDeliver: (Packet) -> Unit = {}

    /**
     * [onSeen] fires for every decoded packet BEFORE the flood/dedup decision -
     * so it observes a peer on every route it is reachable by, even when the
     * duplicate is about to be dropped. Use it for reachability/topology
     * (who is heard over mesh vs server); [onDeliver] still fires once per
     * unique packet for actual handling.
     */
    fun start(onSeen: (Packet, Route) -> Unit = { _, _ -> }, onDeliver: (Packet) -> Unit) {
        this.onDeliver = onDeliver
        val intake = { bytes: ByteArray, route: Route ->
            val packet = try {
                PacketCodec.decode(bytes)
            } catch (e: IllegalArgumentException) {
                null // corrupt frame off the radio - drop silently
            }
            if (packet != null) {
                onSeen(packet, route)
                val result = flood.onReceive(packet)
                if (result.deliver) onDeliver(packet)
                result.forward?.let { transport.broadcast(PacketCodec.encode(it)) }
            }
        }
        // A routed transport tags each packet with its link; a plain one is all mesh.
        if (transport is RoutedTransport) transport.onReceiveRouted(intake)
        else transport.onReceive { bytes -> intake(bytes, Route.MESH) }
    }

    fun send(packet: Packet) {
        flood.markOwn(packet)
        transport.broadcast(PacketCodec.encode(packet))
    }
}
