package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PacketCodecTest {

    @Test
    fun positionRoundTrip() {
        val p = Packet.Position(
            originId = "a1b2c3d4", seqNum = 42, ttl = 4, timestampMs = 1765432100123L,
            lat = 52.52, lon = 13.405, headingDeg = 195.5f
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(p))
        assertEquals(p, decoded)
    }

    @Test
    fun voiceRoundTrip() {
        val opus = byteArrayOf(1, 2, 3, 4, 5, -1, 0, 127)
        val p = Packet.Voice(
            originId = "a1b2c3d4", seqNum = 7, ttl = 4, timestampMs = 1765432100456L,
            clipId = 3, frameNum = 12, isLast = true, opusData = opus, live = true
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(p)) as Packet.Voice
        assertEquals(p.originId, decoded.originId)
        assertEquals(p.seqNum, decoded.seqNum)
        assertEquals(p.ttl, decoded.ttl)
        assertEquals(p.timestampMs, decoded.timestampMs)
        assertEquals(3, decoded.clipId)
        assertEquals(12, decoded.frameNum)
        assertEquals(true, decoded.isLast)
        assertEquals(true, decoded.live)
        assertArrayEquals(opus, decoded.opusData)
    }

    @Test
    fun voiceLiveDefaultsFalseRoundTrip() {
        val p = Packet.Voice("a", 1, 4, 0L, clipId = 1, frameNum = 0,
            isLast = false, opusData = byteArrayOf(9))
        val decoded = PacketCodec.decode(PacketCodec.encode(p)) as Packet.Voice
        assertEquals(false, decoded.live)
    }

    @Test
    fun presenceRoundTrip() {
        val p = Packet.Presence(
            originId = "a1b2c3d4", seqNum = 1, ttl = 4, timestampMs = 1765432100789L,
            name = "Patrick", batteryPct = 87
        )
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun dedupKeyIsOriginAndSeqForPositionAndPresence() {
        val p = Packet.Position("aa", 5, 4, 0L, 0.0, 0.0, 0f)
        assertEquals("aa:5", p.dedupKey)
    }

    @Test
    fun dedupKeyIsOriginClipFrameForVoice() {
        val v = Packet.Voice("aa", 9, 4, 0L, clipId = 2, frameNum = 3,
            isLast = false, opusData = byteArrayOf(1))
        assertEquals("aa:v:2:3", v.dedupKey)
    }

    @Test
    fun withTtlCopiesEveryType() {
        val p = Packet.Position("aa", 5, 4, 0L, 1.0, 2.0, 3f).withTtl(3)
        assertEquals(3, p.ttl)
        assertEquals("aa", p.originId)
    }

    @Test
    fun presenceDedupKeyRoundTrip() {
        val p = Packet.Presence(
            originId = "bb3344", seqNum = 99, ttl = 3, timestampMs = 1765432100000L,
            name = "Alice", batteryPct = 255
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(p)) as Packet.Presence
        assertEquals(p, decoded)
        assertEquals(p.dedupKey, decoded.dedupKey)
    }

    @Test
    fun garbageThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            PacketCodec.decode(byteArrayOf(99, 0, 0))
        }
    }
}
