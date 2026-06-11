package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderBufferTest {

    @Test
    fun inOrderFramesPassStraightThrough() {
        val buf = ReorderBuffer()
        assertEquals(1, buf.offer(0, byteArrayOf(0)).size)
        assertEquals(1, buf.offer(1, byteArrayOf(1)).size)
    }

    @Test
    fun outOfOrderFrameIsHeldUntilGapFills() {
        val buf = ReorderBuffer()
        assertTrue(buf.offer(1, byteArrayOf(1)).isEmpty())       // hole at 0
        val released = buf.offer(0, byteArrayOf(0))               // fills hole
        assertEquals(2, released.size)
        assertArrayEquals(byteArrayOf(0), released[0])
        assertArrayEquals(byteArrayOf(1), released[1])
    }

    @Test
    fun scrambledClipComesOutOrdered() {
        val buf = ReorderBuffer()
        val out = mutableListOf<ByteArray>()
        listOf(2, 0, 3, 1).forEach { n -> out += buf.offer(n, byteArrayOf(n.toByte())) }
        assertEquals(listOf<Byte>(0, 1, 2, 3), out.map { it[0] })
    }

    @Test
    fun duplicatesAndAncientFramesAreDropped() {
        val buf = ReorderBuffer()
        buf.offer(0, byteArrayOf(0))
        assertTrue(buf.offer(0, byteArrayOf(0)).isEmpty())   // already played
        buf.offer(2, byteArrayOf(2))                          // held
        assertTrue(buf.offer(2, byteArrayOf(2)).isEmpty())   // duplicate of held
    }
}
