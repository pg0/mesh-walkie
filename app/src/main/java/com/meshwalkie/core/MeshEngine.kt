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

    fun start(onDeliver: (Packet) -> Unit) {
        this.onDeliver = onDeliver
        transport.onReceive { bytes ->
            val packet = try {
                PacketCodec.decode(bytes)
            } catch (e: IllegalArgumentException) {
                return@onReceive // corrupt frame off the radio - drop silently
            }
            val result = flood.onReceive(packet)
            if (result.deliver) onDeliver(packet)
            result.forward?.let { transport.broadcast(PacketCodec.encode(it)) }
        }
    }

    fun send(packet: Packet) {
        flood.markOwn(packet)
        transport.broadcast(PacketCodec.encode(packet))
    }
}
