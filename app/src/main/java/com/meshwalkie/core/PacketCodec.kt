package com.meshwalkie.core

import java.nio.ByteBuffer

/** Binary wire codec. Big-endian. Identical format on every transport. */
object PacketCodec {
    private const val TYPE_POSITION: Byte = 1
    private const val TYPE_VOICE: Byte = 2
    private const val TYPE_PRESENCE: Byte = 3

    fun encode(p: Packet): ByteArray {
        val originBytes = p.originId.toByteArray(Charsets.UTF_8)
        require(originBytes.size <= 255) { "originId too long" }
        val header = 1 + 1 + originBytes.size + 4 + 1 + 8
        val buf: ByteBuffer = when (p) {
            is Packet.Position -> ByteBuffer.allocate(header + 8 + 8 + 4).also {
                it.put(TYPE_POSITION); putHeader(it, p, originBytes)
                it.putDouble(p.lat); it.putDouble(p.lon); it.putFloat(p.headingDeg)
            }
            is Packet.Voice -> {
                require(p.opusData.size <= 65535) { "voice frame too large" }
                ByteBuffer.allocate(header + 4 + 4 + 1 + 2 + p.opusData.size).also {
                    it.put(TYPE_VOICE); putHeader(it, p, originBytes)
                    it.putInt(p.clipId); it.putInt(p.frameNum)
                    it.put(if (p.isLast) 1 else 0)
                    it.putShort(p.opusData.size.toShort()); it.put(p.opusData)
                }
            }
            is Packet.Presence -> {
                val nameBytes = p.name.toByteArray(Charsets.UTF_8)
                require(nameBytes.size <= 255) { "name too long" }
                ByteBuffer.allocate(header + 1 + nameBytes.size + 1).also {
                    it.put(TYPE_PRESENCE); putHeader(it, p, originBytes)
                    it.put(nameBytes.size.toByte()); it.put(nameBytes)
                    it.put(p.batteryPct.toByte())
                }
            }
        }
        return buf.array()
    }

    fun decode(bytes: ByteArray): Packet {
        try {
            val buf = ByteBuffer.wrap(bytes)
            val type = buf.get()
            val originLen = buf.get().toInt() and 0xFF
            val originId = String(ByteArray(originLen).also { buf.get(it) }, Charsets.UTF_8)
            val seqNum = buf.getInt()
            val ttl = buf.get().toInt()
            val ts = buf.getLong()
            return when (type) {
                TYPE_POSITION -> Packet.Position(
                    originId, seqNum, ttl, ts,
                    lat = buf.getDouble(), lon = buf.getDouble(), headingDeg = buf.getFloat()
                )
                TYPE_VOICE -> {
                    val clipId = buf.getInt()
                    val frameNum = buf.getInt()
                    val isLast = buf.get() == 1.toByte()
                    val len = buf.getShort().toInt() and 0xFFFF
                    val data = ByteArray(len).also { buf.get(it) }
                    Packet.Voice(originId, seqNum, ttl, ts, clipId, frameNum, isLast, data)
                }
                TYPE_PRESENCE -> {
                    val nameLen = buf.get().toInt() and 0xFF
                    val name = String(ByteArray(nameLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Presence(originId, seqNum, ttl, ts, name, buf.get().toInt())
                }
                else -> throw IllegalArgumentException("unknown packet type $type")
            }
        } catch (e: java.nio.BufferUnderflowException) {
            throw IllegalArgumentException("truncated packet", e)
        }
    }

    private fun putHeader(buf: ByteBuffer, p: Packet, originBytes: ByteArray) {
        buf.put(originBytes.size.toByte()); buf.put(originBytes)
        buf.putInt(p.seqNum); buf.put(p.ttl.toByte()); buf.putLong(p.timestampMs)
    }
}
