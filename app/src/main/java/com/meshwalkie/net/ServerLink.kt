package com.meshwalkie.net

import com.meshwalkie.core.Transport
import com.meshwalkie.util.L
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Client side of the internet relay - connects to a [HostServer] (or the
 * standalone relay in server/relay.py) at [host]:[port]. A [Transport]:
 * broadcast() sends to the host, onReceive() delivers packets the host
 * relays.
 *
 * Two wire modes share this one class/thread/reconnect path:
 * - RAW (the original constructor, [wsMode] false): raw TCP,
 *   [4-byte BE length][payload], plus a length==0 keepalive frame in both
 *   directions (NAT idle-timeout survival; dead connections are caught by
 *   the socket read timeout).
 * - WS ([wsMode] true, built via [forAddr]): a WebSocket client (RFC 6455),
 *   TLS ([wsTls]) when the relay sits behind an HTTP/WS-only front like a
 *   Cloudflare Tunnel. One mesh packet ciphertext = one unfragmented binary
 *   WS message; fragmented incoming messages are reassembled. Keepalive is
 *   a masked empty ping every 20s instead of the raw zero-length frame.
 *
 * [autoReconnect] keeps retrying with backoff after a drop - used for the
 * standalone online server, which should just stay connected. Leave it false
 * for the original one-shot mesh-host join: a failed/dropped attempt reports
 * once via [onState] and the caller (auto-join on the next announce, or a
 * manual retry) decides what happens next.
 */
class ServerLink(
    private val host: String,
    private val port: Int,
    private val autoReconnect: Boolean = false,
    private val wsMode: Boolean = false,
    private val wsPath: String = "/",
    private val wsTls: Boolean = false
) : Transport {
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    @Volatile private var running = false
    @Volatile private var handler: ((ByteArray) -> Unit)? = null
    @Volatile var onState: ((connected: Boolean) -> Unit)? = null

    fun connect() {
        running = true
        Thread {
            var backoffMs = 1000L
            while (running) {
                val attemptStart = System.currentTimeMillis()
                try {
                    L.i(TAG, "connecting to [$host]:$port" + if (wsMode) " (${if (wsTls) "wss" else "ws"})" else "")
                    // NOTE: no Socket().apply{} here - inside apply, `port` would
                    // resolve to Socket.getPort() (0 while unconnected), not the
                    // class field, and the connect would target port 0.
                    val plain = Socket()
                    plain.connect(InetSocketAddress(host, port), 8000)
                    socket = plain   // so a failed TLS/WS handshake still gets closed in the finally
                    val s: Socket = if (wsMode && wsTls) {
                        // Layer TLS over the connected socket; passing the
                        // hostname here sets SNI. The read timeout is set
                        // BEFORE startHandshake so a stalled handshake can't
                        // block past it. SSLSocket does NOT verify hostnames
                        // on its own - that check is mandatory and explicit.
                        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        val ssl = factory.createSocket(plain, host, port, true) as SSLSocket
                        ssl.soTimeout = 75_000
                        ssl.startHandshake()
                        val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
                        if (!verifier.verify(host, ssl.session)) {
                            throw IOException("TLS hostname verification failed for $host")
                        }
                        ssl
                    } else {
                        plain
                    }
                    s.soTimeout = 75_000
                    socket = s
                    val o = DataOutputStream(s.getOutputStream())
                    val inp = DataInputStream(BufferedInputStream(s.getInputStream()))

                    if (wsMode) {
                        // Host header carries :port only when it isn't the
                        // scheme default (443 for wss, 80 for ws).
                        val hostHeader = if ((wsTls && port == 443) || (!wsTls && port == 80)) host else "$host:$port"
                        val key = WsWire.randomKey()
                        o.write(WsWire.buildHandshakeRequest(wsPath, hostHeader, key))
                        o.flush()
                        WsWire.readHandshakeResponse(inp, WsWire.acceptKeyFor(key))
                    }
                    out = o
                    L.i(TAG, "connected to [$host]:$port")
                    onState?.invoke(true)

                    // Keepalive: every 20s, either a zero-length raw frame or
                    // a masked empty WS ping - both keep NAT/firewall mappings
                    // alive on links that carry no voice traffic for a while.
                    // Shares the write lock with broadcast() so frames never
                    // interleave.
                    val keepalive = Thread {
                        try {
                            while (running && socket === s) {
                                Thread.sleep(20_000)
                                if (!running || socket !== s) break
                                if (wsMode) {
                                    val ping = WsWire.encodeFrame(WsWire.OP_PING, ByteArray(0), masked = true)
                                    synchronized(o) { o.write(ping); o.flush() }
                                } else {
                                    synchronized(o) { o.writeInt(0); o.flush() }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                    keepalive.isDaemon = true
                    keepalive.start()

                    if (wsMode) {
                        readLoop@ while (running) {
                            when (val ev = WsWire.readMessage(inp)) {
                                is WsWire.WsEvent.Message -> {
                                    if (ev.opcode == WsWire.OP_BINARY) handler?.invoke(ev.payload)
                                    // text ignored per contract
                                }
                                is WsWire.WsEvent.Ping -> {
                                    val pong = WsWire.encodeFrame(WsWire.OP_PONG, ev.payload, masked = true)
                                    synchronized(o) { o.write(pong); o.flush() }
                                }
                                is WsWire.WsEvent.Pong -> {
                                    // ignored
                                }
                                is WsWire.WsEvent.Close -> break@readLoop
                            }
                        }
                    } else {
                        while (running) {
                            val len = inp.readInt()
                            if (len == 0) continue   // keepalive, not a packet
                            if (len < 0 || len > 200_000) break
                            val b = ByteArray(len); inp.readFully(b)
                            handler?.invoke(b)
                        }
                    }
                } catch (e: Exception) {
                    L.w(TAG, "connect/read failed for [$host]:$port: $e")
                } finally {
                    onState?.invoke(false)
                    closeSocket()
                }
                if (!autoReconnect || !running) break
                // A connection that stayed up a while gets a fresh backoff; one
                // that died immediately keeps backing off up to 30s.
                if (System.currentTimeMillis() - attemptStart >= 5_000L) backoffMs = 1000L
                try { Thread.sleep(backoffMs) } catch (_: InterruptedException) { break }
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }.start()
    }

    override fun broadcast(bytes: ByteArray) {
        try {
            val o = out ?: return
            if (wsMode) {
                val frame = WsWire.encodeFrame(WsWire.OP_BINARY, bytes, masked = true)
                synchronized(o) { o.write(frame); o.flush() }
            } else {
                synchronized(o) { o.writeInt(bytes.size); o.write(bytes); o.flush() }
            }
        } catch (_: Exception) {
        }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) { this.handler = handler }

    /** Stop for good: no more reconnect attempts even with [autoReconnect]. */
    fun close() {
        running = false
        closeSocket()
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; out = null
    }

    companion object {
        private const val TAG = "ServerLink"

        /**
         * Build a ServerLink from a parsed [NetUtil.ServerAddr]: RAW keeps the
         * plain length-framed TCP path, WS speaks WebSocket client (TLS when
         * [NetUtil.ServerAddr.tls] is set).
         */
        fun forAddr(addr: NetUtil.ServerAddr, autoReconnect: Boolean = false): ServerLink = ServerLink(
            host = addr.host,
            port = addr.port,
            autoReconnect = autoReconnect,
            wsMode = addr.kind == NetUtil.ServerKind.WS,
            wsPath = addr.path,
            wsTls = addr.tls
        )
    }
}
