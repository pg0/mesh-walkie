package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodControllerTest {

    private fun position(seq: Int, ttl: Int) =
        Packet.Position("origin1", seq, ttl, 1000L, 52.52, 13.405, 0f)

    @Test
    fun newPacketIsDeliveredAndForwardedWithTtlMinusOne() {
        val flood = FloodController()
        val result = flood.onReceive(position(seq = 1, ttl = 4))
        assertTrue(result.deliver)
        assertEquals(3, result.forward!!.ttl)
        assertEquals("origin1", result.forward!!.originId)
    }

    @Test
    fun duplicateIsDroppedEntirely() {
        val flood = FloodController()
        flood.onReceive(position(seq = 1, ttl = 4))
        val second = flood.onReceive(position(seq = 1, ttl = 4))
        assertFalse(second.deliver)
        assertNull(second.forward)
    }

    @Test
    fun ttlZeroIsDeliveredButNotForwarded() {
        val flood = FloodController()
        val result = flood.onReceive(position(seq = 2, ttl = 0))
        assertTrue(result.deliver)
        assertNull(result.forward)
    }

    @Test
    fun ownPacketMarkedSoEchoIsDropped() {
        val flood = FloodController()
        val mine = position(seq = 3, ttl = 4)
        flood.markOwn(mine)
        val echo = flood.onReceive(mine.withTtl(3)) // echo back from a neighbor
        assertFalse(echo.deliver)
        assertNull(echo.forward)
    }

    @Test
    fun voiceFramesDedupByClipAndFrameNotSeq() {
        val flood = FloodController()
        val f0 = Packet.Voice("o", 10, 4, 0L, clipId = 1, frameNum = 0, isLast = false, opusData = byteArrayOf(1))
        val f0again = Packet.Voice("o", 99, 4, 0L, clipId = 1, frameNum = 0, isLast = false, opusData = byteArrayOf(1))
        assertTrue(flood.onReceive(f0).deliver)
        assertFalse(flood.onReceive(f0again).deliver) // same clip+frame, different seq -> dup
    }
}
