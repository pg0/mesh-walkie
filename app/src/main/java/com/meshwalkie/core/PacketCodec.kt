package com.meshwalkie.core

import java.nio.ByteBuffer

/** Binary wire codec. Big-endian. Identical format on every transport. */
object PacketCodec {
    private const val TYPE_POSITION: Byte = 1
    private const val TYPE_VOICE: Byte = 2
    private const val TYPE_PRESENCE: Byte = 3
    private const val TYPE_WAYPOINT: Byte = 4
    private const val TYPE_TEXT: Byte = 5
    private const val TYPE_ACK: Byte = 6
    private const val TYPE_HOST: Byte = 7
    private const val TYPE_TIMER: Byte = 8

    fun encode(p: Packet): ByteArray {
        val originBytes = p.originId.toByteArray(Charsets.UTF_8)
        require(originBytes.size <= 255) { "originId too long" }
        val header = 1 + 1 + originBytes.size + 4 + 1 + 8
        val buf: ByteBuffer = when (p) {
            is Packet.Position -> ByteBuffer.allocate(header + 8 + 8 + 4 + 4 + 4).also {
                it.put(TYPE_POSITION); putHeader(it, p, originBytes)
                it.putDouble(p.lat); it.putDouble(p.lon); it.putFloat(p.headingDeg)
                it.putFloat(p.speedMps); it.putFloat(p.courseDeg)
            }
            is Packet.Voice -> {
                require(p.opusData.size <= 65535) { "voice frame too large" }
                ByteBuffer.allocate(header + 4 + 4 + 1 + 1 + 2 + p.opusData.size).also {
                    it.put(TYPE_VOICE); putHeader(it, p, originBytes)
                    it.putInt(p.clipId); it.putInt(p.frameNum)
                    it.put(if (p.isLast) 1 else 0)
                    it.put(if (p.live) 1 else 0)
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
            is Packet.Text -> {
                val nameBytes = p.senderName.toByteArray(Charsets.UTF_8)
                val textBytes = p.text.toByteArray(Charsets.UTF_8)
                require(nameBytes.size <= 255) { "name too long" }
                require(textBytes.size <= 65535) { "text too long" }
                ByteBuffer.allocate(header + 1 + nameBytes.size + 2 + textBytes.size).also {
                    it.put(TYPE_TEXT); putHeader(it, p, originBytes)
                    it.put(nameBytes.size.toByte()); it.put(nameBytes)
                    it.putShort(textBytes.size.toShort()); it.put(textBytes)
                }
            }
            is Packet.Waypoint -> {
                val nameBytes = p.senderName.toByteArray(Charsets.UTF_8)
                val labelBytes = p.label.toByteArray(Charsets.UTF_8)
                require(nameBytes.size <= 255) { "name too long" }
                require(labelBytes.size <= 255) { "label too long" }
                ByteBuffer.allocate(header + 1 + nameBytes.size + 8 + 8 + 1 + labelBytes.size).also {
                    it.put(TYPE_WAYPOINT); putHeader(it, p, originBytes)
                    it.put(nameBytes.size.toByte()); it.put(nameBytes)
                    it.putDouble(p.lat); it.putDouble(p.lon)
                    it.put(labelBytes.size.toByte()); it.put(labelBytes)
                }
            }
            is Packet.Ack -> {
                val refBytes = p.refOriginId.toByteArray(Charsets.UTF_8)
                require(refBytes.size <= 255) { "refOriginId too long" }
                ByteBuffer.allocate(header + 1 + refBytes.size + 4).also {
                    it.put(TYPE_ACK); putHeader(it, p, originBytes)
                    it.put(refBytes.size.toByte()); it.put(refBytes)
                    it.putInt(p.refClipId)
                }
            }
            is Packet.Timer -> {
                val labelBytes = p.label.toByteArray(Charsets.UTF_8)
                require(labelBytes.size <= 255) { "label too long" }
                ByteBuffer.allocate(header + 1 + labelBytes.size + 4).also {
                    it.put(TYPE_TIMER); putHeader(it, p, originBytes)
                    it.put(labelBytes.size.toByte()); it.put(labelBytes)
                    it.putInt(p.durationSec)
                }
            }
            is Packet.Host -> {
                val nameBytes = p.name.toByteArray(Charsets.UTF_8)
                val ipBytes = p.ip.toByteArray(Charsets.UTF_8)
                require(nameBytes.size <= 255) { "name too long" }
                require(ipBytes.size <= 255) { "ip too long" }
                ByteBuffer.allocate(header + 1 + nameBytes.size + 1 + ipBytes.size + 4).also {
                    it.put(TYPE_HOST); putHeader(it, p, originBytes)
                    it.put(nameBytes.size.toByte()); it.put(nameBytes)
                    it.put(ipBytes.size.toByte()); it.put(ipBytes)
                    it.putInt(p.port)
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
            val ttl = buf.get().toInt() and 0xFF
            val ts = buf.getLong()
            return when (type) {
                TYPE_POSITION -> Packet.Position(
                    originId, seqNum, ttl, ts,
                    lat = buf.getDouble(), lon = buf.getDouble(), headingDeg = buf.getFloat(),
                    speedMps = buf.getFloat(), courseDeg = buf.getFloat()
                )
                TYPE_VOICE -> {
                    val clipId = buf.getInt()
                    val frameNum = buf.getInt()
                    val isLast = buf.get() == 1.toByte()
                    val live = buf.get() == 1.toByte()
                    val len = buf.getShort().toInt() and 0xFFFF
                    val data = ByteArray(len).also { buf.get(it) }
                    Packet.Voice(originId, seqNum, ttl, ts, clipId, frameNum, isLast, data, live)
                }
                TYPE_PRESENCE -> {
                    val nameLen = buf.get().toInt() and 0xFF
                    val name = String(ByteArray(nameLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Presence(originId, seqNum, ttl, ts, name, buf.get().toInt() and 0xFF)
                }
                TYPE_TEXT -> {
                    val nameLen = buf.get().toInt() and 0xFF
                    val name = String(ByteArray(nameLen).also { buf.get(it) }, Charsets.UTF_8)
                    val textLen = buf.getShort().toInt() and 0xFFFF
                    val text = String(ByteArray(textLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Text(originId, seqNum, ttl, ts, name, text)
                }
                TYPE_WAYPOINT -> {
                    val nameLen = buf.get().toInt() and 0xFF
                    val name = String(ByteArray(nameLen).also { buf.get(it) }, Charsets.UTF_8)
                    val lat = buf.getDouble(); val lon = buf.getDouble()
                    val labelLen = buf.get().toInt() and 0xFF
                    val label = String(ByteArray(labelLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Waypoint(originId, seqNum, ttl, ts, name, lat, lon, label)
                }
                TYPE_ACK -> {
                    val refLen = buf.get().toInt() and 0xFF
                    val refOrigin = String(ByteArray(refLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Ack(originId, seqNum, ttl, ts, refOrigin, buf.getInt())
                }
                TYPE_TIMER -> {
                    val llen = buf.get().toInt() and 0xFF
                    val label = String(ByteArray(llen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Timer(originId, seqNum, ttl, ts, label, buf.getInt())
                }
                TYPE_HOST -> {
                    val nameLen = buf.get().toInt() and 0xFF
                    val name = String(ByteArray(nameLen).also { buf.get(it) }, Charsets.UTF_8)
                    val ipLen = buf.get().toInt() and 0xFF
                    val ip = String(ByteArray(ipLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Host(originId, seqNum, ttl, ts, name, ip, buf.getInt())
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
