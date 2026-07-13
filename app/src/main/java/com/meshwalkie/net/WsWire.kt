package com.meshwalkie.net

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

/**
 * Pure-JVM RFC 6455 WebSocket client primitives: framing, masking, and the
 * opening handshake. Deliberately free of Android imports so it can be unit
 * tested on the plain JVM without a device/emulator. ServerLink wires these
 * onto a real Socket/SSLSocket; this object only ever touches bytes.
 */
object WsWire {
    const val OP_CONTINUATION = 0x0
    const val OP_TEXT = 0x1
    const val OP_BINARY = 0x2
    const val OP_CLOSE = 0x8
    const val OP_PING = 0x9
    const val OP_PONG = 0xA

    /** Cap on a fully reassembled message - matches the mesh packet size cap
     * used by the raw-TCP wire format, so both transports share one limit. */
    const val MAX_MESSAGE = 200_000

    private const val HANDSHAKE_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val MAX_HANDSHAKE_HEADER = 16_384

    /** Sec-WebSocket-Accept value the server must answer with for [key]. */
    fun acceptKeyFor(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val digest = sha1.digest((key + HANDSHAKE_GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    /** A fresh base64-encoded 16-byte Sec-WebSocket-Key for a client handshake. */
    fun randomKey(): String = Base64.getEncoder().encodeToString(Random.nextBytes(16))

    /** One decoded WS frame off the wire (already unmasked, if it was masked). */
    data class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

    /**
     * Encode one WS frame. [masked] must be true for client->server frames -
     * Cloudflare's edge (and the RFC) require it - and false for
     * server->client frames. Length uses the standard RFC 6455 escalation:
     * 7-bit for <126 bytes, a 16-bit BE length for <65536, else a 64-bit BE
     * length (the top bytes are always zero here since payloads are capped
     * far below Int range).
     */
    fun encodeFrame(opcode: Int, payload: ByteArray, masked: Boolean, fin: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 14)
        out.write((if (fin) 0x80 else 0x00) or (opcode and 0x0F))
        val maskBit = if (masked) 0x80 else 0x00
        val len = payload.size
        when {
            len < 126 -> out.write(maskBit or len)
            len < 65536 -> {
                out.write(maskBit or 126)
                out.write((len ushr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(maskBit or 127)
                for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) {
                    out.write(((len.toLong() ushr shift) and 0xFF).toInt())
                }
            }
        }
        if (masked) {
            val mask = Random.nextBytes(4)
            out.write(mask)
            val out2 = ByteArray(len)
            for (i in 0 until len) out2[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            out.write(out2)
        } else {
            out.write(payload)
        }
        return out.toByteArray()
    }

    /** Read exactly one frame off [inp], unmasking it if the mask bit is set. */
    fun readFrame(inp: DataInputStream): Frame {
        val b0 = inp.readUnsignedByte()
        val fin = (b0 and 0x80) != 0
        val opcode = b0 and 0x0F
        val b1 = inp.readUnsignedByte()
        val masked = (b1 and 0x80) != 0
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = (inp.readUnsignedByte().toLong() shl 8) or inp.readUnsignedByte().toLong()
        } else if (len == 127L) {
            len = 0L
            repeat(8) { len = (len shl 8) or inp.readUnsignedByte().toLong() }
        }
        if (len < 0 || len > MAX_MESSAGE) throw IOException("WS frame too large: $len")
        val maskKey = if (masked) ByteArray(4).also { inp.readFully(it) } else null
        val payload = ByteArray(len.toInt())
        inp.readFully(payload)
        if (maskKey != null) {
            for (i in payload.indices) payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
        }
        return Frame(fin, opcode, payload)
    }

    /**
     * One logical WS event handed to the caller by [readMessage]: either a
     * fully reassembled data message, or a control frame the caller reacts
     * to per the contract (ping -> reply pong, close -> end the connection;
     * pong and text carry nothing this client acts on).
     */
    sealed class WsEvent {
        data class Message(val opcode: Int, val payload: ByteArray) : WsEvent()
        data class Ping(val payload: ByteArray) : WsEvent()
        data class Pong(val payload: ByteArray) : WsEvent()
        data class Close(val payload: ByteArray) : WsEvent()
    }

    /**
     * Read one logical WS event off [inp]. Data messages (opcode TEXT/BINARY)
     * are reassembled across continuation frames until FIN, accumulating up
     * to [MAX_MESSAGE] bytes total (exceeding it throws, same as any other
     * read failure - the caller's reconnect/backoff loop handles it). Control
     * frames (ping/pong/close) arrive as single unfragmented frames per RFC
     * 6455 and may appear interleaved between fragments of a data message; if
     * one appears before the data message's FIN, it is returned immediately
     * without disturbing the in-progress reassembly.
     */
    fun readMessage(inp: DataInputStream): WsEvent {
        var opcode = -1
        val buf = ByteArrayOutputStream()
        while (true) {
            val f = readFrame(inp)
            when (f.opcode) {
                OP_PING -> return WsEvent.Ping(f.payload)
                OP_PONG -> return WsEvent.Pong(f.payload)
                OP_CLOSE -> return WsEvent.Close(f.payload)
                OP_CONTINUATION -> {
                    if (opcode == -1) throw IOException("WS continuation frame with no start frame")
                    buf.write(f.payload)
                }
                else -> {
                    opcode = f.opcode
                    buf.write(f.payload)
                }
            }
            if (buf.size() > MAX_MESSAGE) throw IOException("WS message too large: > $MAX_MESSAGE bytes")
            if (f.fin) return WsEvent.Message(opcode, buf.toByteArray())
        }
    }

    /** Client handshake request bytes: GET [path] with [hostHeader] and a fresh [key]. */
    fun buildHandshakeRequest(path: String, hostHeader: String, key: String): ByteArray {
        val req = buildString {
            append("GET ").append(path).append(" HTTP/1.1\r\n")
            append("Host: ").append(hostHeader).append("\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: ").append(key).append("\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        return req.toByteArray(Charsets.US_ASCII)
    }

    /**
     * Read + validate the server's handshake response off [inp]: headers
     * only, capped at 16 KB (a plain upgrade response never gets close to
     * that). Requires HTTP status 101 and a Sec-WebSocket-Accept matching
     * [expectedAcceptKey] (header names compared case-insensitively);
     * anything else throws, which the caller's connect loop treats like any
     * other connect failure.
     */
    fun readHandshakeResponse(inp: DataInputStream, expectedAcceptKey: String) {
        val header = StringBuilder()
        while (true) {
            val b = inp.readUnsignedByte()
            header.append(b.toChar())
            val n = header.length
            if (n > MAX_HANDSHAKE_HEADER) throw IOException("WS handshake response too large")
            if (n >= 4 &&
                header[n - 4] == '\r' && header[n - 3] == '\n' &&
                header[n - 2] == '\r' && header[n - 1] == '\n'
            ) break
        }
        val lines = header.toString().split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty() || !lines[0].contains(" 101 ")) {
            throw IOException("WS handshake failed: ${lines.firstOrNull() ?: "empty response"}")
        }
        var accept: String? = null
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx < 0) continue
            if (lines[i].substring(0, idx).trim().equals("Sec-WebSocket-Accept", ignoreCase = true)) {
                accept = lines[i].substring(idx + 1).trim()
            }
        }
        if (accept != expectedAcceptKey) throw IOException("WS Sec-WebSocket-Accept mismatch")
    }
}
