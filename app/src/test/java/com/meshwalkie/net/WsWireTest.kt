package com.meshwalkie.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import kotlin.random.Random

/**
 * Pure-JVM tests for the WsWire framing/handshake primitives - no sockets,
 * just byte arrays in and out, so they exercise the exact wire format (RFC
 * 6455 length escalation, masking, control opcodes, fragmentation).
 */
class WsWireTest {

    @Test
    fun acceptKeyMatchesRfc6455Vector() {
        // The canonical example from RFC 6455 section 1.3.
        assertEquals(
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            WsWire.acceptKeyFor("dGhlIHNhbXBsZSBub25jZQ==")
        )
    }

    private fun roundTrip(size: Int, masked: Boolean) {
        val payload = Random.nextBytes(size)
        val frame = WsWire.encodeFrame(WsWire.OP_BINARY, payload, masked = masked)
        val inp = DataInputStream(ByteArrayInputStream(frame))
        val read = WsWire.readFrame(inp)
        assertTrue(read.fin)
        assertEquals(WsWire.OP_BINARY, read.opcode)
        assertArrayEquals(payload, read.payload)
    }

    @Test fun roundTripSmallMasked() = roundTrip(5, masked = true)
    @Test fun roundTripSmallUnmasked() = roundTrip(5, masked = false)
    @Test fun roundTripMediumMasked() = roundTrip(300, masked = true)
    @Test fun roundTripMediumUnmasked() = roundTrip(300, masked = false)
    @Test fun roundTripLargeMasked() = roundTrip(70_000, masked = true)
    @Test fun roundTripLargeUnmasked() = roundTrip(70_000, masked = false)

    @Test
    fun controlOpcodesRoundTrip() {
        for (opcode in intArrayOf(WsWire.OP_PING, WsWire.OP_PONG, WsWire.OP_CLOSE)) {
            val payload = byteArrayOf(9, 8, 7)
            val frame = WsWire.encodeFrame(opcode, payload, masked = true)
            val inp = DataInputStream(ByteArrayInputStream(frame))
            val read = WsWire.readFrame(inp)
            assertEquals(opcode, read.opcode)
            assertArrayEquals(payload, read.payload)
        }
    }

    @Test
    fun fragmentedMessageReassembles() {
        val part1 = Random.nextBytes(4000)
        val part2 = Random.nextBytes(3000)
        val out = ByteArrayOutputStream()
        out.write(WsWire.encodeFrame(WsWire.OP_BINARY, part1, masked = false, fin = false))
        out.write(WsWire.encodeFrame(WsWire.OP_CONTINUATION, part2, masked = false, fin = true))
        val inp = DataInputStream(ByteArrayInputStream(out.toByteArray()))
        val ev = WsWire.readMessage(inp)
        assertTrue(ev is WsWire.WsEvent.Message)
        val msg = ev as WsWire.WsEvent.Message
        assertEquals(WsWire.OP_BINARY, msg.opcode)
        assertArrayEquals(part1 + part2, msg.payload)
    }

    @Test
    fun controlFrameBetweenFragmentsSurfacesImmediately() {
        // A control frame can legally appear between data fragments per RFC
        // 6455; readMessage must surface it right away rather than folding
        // it into the in-progress reassembly.
        val out = ByteArrayOutputStream()
        out.write(WsWire.encodeFrame(WsWire.OP_PING, ByteArray(0), masked = false))
        val inp = DataInputStream(ByteArrayInputStream(out.toByteArray()))
        val ev = WsWire.readMessage(inp)
        assertTrue(ev is WsWire.WsEvent.Ping)
    }

    @Test
    fun closeAndPongOpcodesPreservedThroughReadMessage() {
        val closeFrame = WsWire.encodeFrame(WsWire.OP_CLOSE, byteArrayOf(1, 2), masked = false)
        val closeEv = WsWire.readMessage(DataInputStream(ByteArrayInputStream(closeFrame)))
        assertTrue(closeEv is WsWire.WsEvent.Close)

        val pongFrame = WsWire.encodeFrame(WsWire.OP_PONG, byteArrayOf(3, 4), masked = false)
        val pongEv = WsWire.readMessage(DataInputStream(ByteArrayInputStream(pongFrame)))
        assertTrue(pongEv is WsWire.WsEvent.Pong)
    }
}
