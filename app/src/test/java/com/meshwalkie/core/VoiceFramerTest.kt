package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceFramerTest {

    // 3 x 20 ms packets per frame for short tests
    private fun framer() = VoiceFramer(frameDurationMs = 60, packetDurationMs = 20)

    @Test
    fun emitsFrameWhenDurationReached() {
        val f = framer()
        assertNull(f.add(byteArrayOf(1)))
        assertNull(f.add(byteArrayOf(2, 2)))
        val frame = f.add(byteArrayOf(3, 3, 3))
        assertNotNull(frame)
    }

    @Test
    fun flushReturnsRemainderOrNull() {
        val f = framer()
        f.add(byteArrayOf(1))
        assertNotNull(f.flush())
        assertNull(f.flush())   // empty now
    }

    @Test
    fun unpackRestoresOriginalPackets() {
        val f = framer()
        f.add(byteArrayOf(1))
        f.add(byteArrayOf(2, 2))
        val frame = f.add(byteArrayOf(3, 3, 3))!!
        val packets = VoiceFramer.unpack(frame)
        assertEquals(3, packets.size)
        assertArrayEquals(byteArrayOf(1), packets[0])
        assertArrayEquals(byteArrayOf(2, 2), packets[1])
        assertArrayEquals(byteArrayOf(3, 3, 3), packets[2])
    }

    @Test
    fun defaultIsOneSecondFrames() {
        val f = VoiceFramer() // 1000 ms / 20 ms = 50 packets per frame
        repeat(49) { assertNull(f.add(byteArrayOf(0))) }
        assertNotNull(f.add(byteArrayOf(0)))
    }
}
