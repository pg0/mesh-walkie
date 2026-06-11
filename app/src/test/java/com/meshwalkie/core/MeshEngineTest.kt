package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshEngineTest {

    private class Node(val id: String) {
        val transport = FakeTransport()
        val engine = MeshEngine(transport)
        val delivered = mutableListOf<Packet>()
        init { engine.start { delivered += it } }
    }

    private fun position(origin: String, seq: Int, ttl: Int) =
        Packet.Position(origin, seq, ttl, 0L, 52.52, 13.405, 0f)

    @Test
    fun packetHopsAcrossThreeChainedNodes() {
        // A - B - C : A and C are NOT linked, C only reachable via B's relay
        val a = Node("A"); val b = Node("B"); val c = Node("C")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)

        a.engine.send(position("A", seq = 1, ttl = 4))

        assertEquals(1, b.delivered.size)
        assertEquals(1, c.delivered.size)              // hopped through B
        assertEquals("A", c.delivered[0].originId)
        // sender broadcasts ttl 4 unmodified; B delivers ttl 4, forwards ttl 3
        assertEquals(3, c.delivered[0].ttl)
    }

    @Test
    fun dedupDeliversExactlyOnceInATriangleLoop() {
        // A - B, B - C, C - A : full loop, flooding must not storm
        val a = Node("A"); val b = Node("B"); val c = Node("C")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)
        FakeTransport.link(c.transport, a.transport)

        a.engine.send(position("A", seq = 1, ttl = 4))

        assertEquals(1, b.delivered.size)   // exactly once despite two paths
        assertEquals(1, c.delivered.size)
        assertEquals(0, a.delivered.size)   // own echo dropped, never self-delivered
    }

    @Test
    fun ttlStopsForwardingAtTheEdge() {
        // A - B - C - D chain, ttl=1: B receives ttl1 + forwards ttl0,
        // C receives ttl0 + delivers but does NOT forward, D hears nothing.
        val a = Node("A"); val b = Node("B"); val c = Node("C"); val d = Node("D")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)
        FakeTransport.link(c.transport, d.transport)

        a.engine.send(position("A", seq = 1, ttl = 1))

        assertEquals(1, b.delivered.size)
        assertEquals(1, c.delivered.size)
        assertEquals(0, c.delivered[0].ttl)
        assertTrue(d.delivered.isEmpty())
    }

    @Test
    fun voiceFramesRelayAndDedupByClipFrame() {
        val a = Node("A"); val b = Node("B"); val c = Node("C")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)

        val frame = Packet.Voice("A", 1, 4, 0L, clipId = 7, frameNum = 0,
            isLast = false, opusData = byteArrayOf(10, 20, 30))
        a.engine.send(frame)
        a.engine.send(frame.copy(seqNum = 2))  // re-send same clip+frame -> dup at receivers

        assertEquals(1, c.delivered.size)
        val v = c.delivered[0] as Packet.Voice
        assertEquals(7, v.clipId)
        assertTrue(byteArrayOf(10, 20, 30).contentEquals(v.opusData))
    }

    @Test
    fun corruptBytesAreIgnoredNotFatal() {
        val a = Node("A"); val b = Node("B")
        FakeTransport.link(a.transport, b.transport)
        a.transport.broadcast(byteArrayOf(99, 1, 2))   // garbage straight onto the wire
        assertTrue(b.delivered.isEmpty())              // no crash, no delivery
    }
}
