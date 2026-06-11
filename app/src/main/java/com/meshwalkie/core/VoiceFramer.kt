package com.meshwalkie.core

import java.nio.ByteBuffer

/**
 * Groups 20 ms Opus packets into ~1 s mesh frames so long clips stream
 * through hops instead of waiting for the whole clip.
 * Frame layout: repeated [len 2B big-endian][opus packet bytes].
 */
class VoiceFramer(
    frameDurationMs: Int = 1000,
    packetDurationMs: Int = 20
) {
    private val packetsPerFrame = frameDurationMs / packetDurationMs
    private val pending = mutableListOf<ByteArray>()

    /** @return a packed frame when full, else null. */
    fun add(opusPacket: ByteArray): ByteArray? {
        pending += opusPacket
        return if (pending.size >= packetsPerFrame) packAndClear() else null
    }

    /** @return the final partial frame, or null if nothing pending. */
    fun flush(): ByteArray? = if (pending.isEmpty()) null else packAndClear()

    private fun packAndClear(): ByteArray {
        val size = pending.sumOf { 2 + it.size }
        val buf = ByteBuffer.allocate(size)
        pending.forEach { buf.putShort(it.size.toShort()); buf.put(it) }
        pending.clear()
        return buf.array()
    }

    companion object {
        fun unpack(frame: ByteArray): List<ByteArray> {
            val buf = ByteBuffer.wrap(frame)
            val packets = mutableListOf<ByteArray>()
            while (buf.remaining() >= 2) {
                val len = buf.getShort().toInt() and 0xFFFF
                packets += ByteArray(len).also { buf.get(it) }
            }
            return packets
        }
    }
}
