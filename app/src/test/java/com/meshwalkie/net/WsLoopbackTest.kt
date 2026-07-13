package com.meshwalkie.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Loopback test for WS mode: a minimal in-test WebSocket relay (server side
 * of the RFC 6455 handshake, binary-message relay, ping/pong) built on
 * WsWire, with two real ServerLink instances in ws:// mode talking to it
 * over localhost. Confirms the client-side handshake, masked
 * client->server framing, unmasked server->client framing, and that a
 * server-initiated ping never surfaces as a packet.
 */
class WsLoopbackTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** Bare-bones WS test server: does the server side of the handshake,
     * relays binary messages between connected clients unmasked, and
     * answers client pings with pongs (also sends one ping of its own). */
    private class TestWsServer(port: Int) {
        private val server = ServerSocket(port)
        @Volatile private var running = true
        private val clients = mutableListOf<DataOutputStream>()
        private val lock = Any()

        fun start() {
            Thread {
                while (running) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    Thread { handleClient(socket) }.apply { isDaemon = true }.start()
                }
            }.apply { isDaemon = true }.start()
        }

        private fun handleClient(socket: Socket) {
            var out: DataOutputStream? = null
            try {
                val inp = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val o = DataOutputStream(socket.getOutputStream())
                out = o
                val key = readHandshakeRequest(inp) ?: return
                val accept = WsWire.acceptKeyFor(key)
                o.write(
                    ("HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.US_ASCII)
                )
                o.flush()
                synchronized(lock) { clients.add(o) }

                // Server-initiated ping shortly after connect - the test
                // asserts this is swallowed by the client read loop and never
                // surfaces as a data packet.
                Thread {
                    try {
                        Thread.sleep(200)
                        val ping = WsWire.encodeFrame(WsWire.OP_PING, ByteArray(0), masked = false)
                        synchronized(o) { o.write(ping); o.flush() }
                    } catch (_: Exception) {
                    }
                }.apply { isDaemon = true }.start()

                readLoop@ while (running) {
                    when (val ev = WsWire.readMessage(inp)) {
                        is WsWire.WsEvent.Message -> {
                            if (ev.opcode == WsWire.OP_BINARY) {
                                val frame = WsWire.encodeFrame(WsWire.OP_BINARY, ev.payload, masked = false)
                                synchronized(lock) {
                                    for (c in clients) if (c !== o) synchronized(c) { c.write(frame); c.flush() }
                                }
                            }
                        }
                        is WsWire.WsEvent.Ping -> {
                            val pong = WsWire.encodeFrame(WsWire.OP_PONG, ev.payload, masked = false)
                            synchronized(o) { o.write(pong); o.flush() }
                        }
                        is WsWire.WsEvent.Pong -> { }
                        is WsWire.WsEvent.Close -> break@readLoop
                    }
                }
            } catch (_: Exception) {
            } finally {
                out?.let { o -> synchronized(lock) { clients.remove(o) } }
                try { socket.close() } catch (_: Exception) {}
            }
        }

        private fun readHandshakeRequest(inp: DataInputStream): String? {
            val header = StringBuilder()
            while (true) {
                val b = inp.readUnsignedByte()
                header.append(b.toChar())
                val n = header.length
                if (n > 16_384) return null
                if (n >= 4 && header[n - 4] == '\r' && header[n - 3] == '\n' &&
                    header[n - 2] == '\r' && header[n - 1] == '\n'
                ) break
            }
            for (line in header.toString().split("\r\n")) {
                val idx = line.indexOf(':')
                if (idx < 0) continue
                if (line.substring(0, idx).trim().equals("Sec-WebSocket-Key", ignoreCase = true)) {
                    return line.substring(idx + 1).trim()
                }
            }
            return null
        }

        fun stop() {
            running = false
            try { server.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun broadcastReachesOtherClientAndServerPingIsSwallowed() {
        val port = freePort()
        val server = TestWsServer(port)
        server.start()
        Thread.sleep(300)   // let the accept loop bind

        val received = LinkedBlockingQueue<ByteArray>()
        val addr = NetUtil.ServerAddr(NetUtil.ServerKind.WS, "127.0.0.1", port, "/", tls = false)
        // autoReconnect: a transient localhost connect failure (Windows dev
        // boxes: Hyper-V excluded ephemeral-port ranges throw BindException)
        // retries instead of silently failing the test.
        val a = ServerLink.forAddr(addr, autoReconnect = true)
        val b = ServerLink.forAddr(addr, autoReconnect = true)
        b.onReceive { received.add(it) }
        val aUp = CountDownLatch(1)
        val bUp = CountDownLatch(1)
        a.onState = { if (it) aUp.countDown() }
        b.onState = { if (it) bUp.countDown() }
        a.connect()
        b.connect()
        assertTrue("client A never completed the WS handshake", aUp.await(15, TimeUnit.SECONDS))
        assertTrue("client B never completed the WS handshake", bUp.await(15, TimeUnit.SECONDS))

        val payload = byteArrayOf(10, 20, 30, 40, 50)
        a.broadcast(payload)
        val got = received.poll(3, TimeUnit.SECONDS)
        assertNotNull(got)
        assertArrayEquals(payload, got)

        // The server's own ping (sent ~200ms after connect) must not surface
        // as a received packet on either client.
        assertNull(received.poll(500, TimeUnit.MILLISECONDS))

        a.close()
        b.close()
        server.stop()
    }
}
