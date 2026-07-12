package com.meshwalkie.net

import com.meshwalkie.core.Transport
import com.meshwalkie.util.L
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Client side of the internet relay - connects to a [HostServer] (or the
 * standalone relay in server/relay.py) at [host]:[port]. A [Transport]:
 * broadcast() sends to the host, onReceive() delivers packets the host
 * relays. Same length-framed wire format as HostServer, plus a length==0
 * keepalive frame in both directions (NAT idle-timeout survival, and dead
 * connections are caught by the socket read timeout).
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
    private val autoReconnect: Boolean = false
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
                    L.i(TAG, "connecting to [$host]:$port")
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 8000)
                    s.soTimeout = 75_000
                    socket = s
                    val o = DataOutputStream(s.getOutputStream())
                    out = o
                    L.i(TAG, "connected to [$host]:$port")
                    onState?.invoke(true)

                    // Keepalive: a zero-length frame every 20s keeps NAT/firewall
                    // mappings alive on links that carry no voice traffic for a
                    // while. Shares the write lock with broadcast() so frames
                    // never interleave.
                    val keepalive = Thread {
                        try {
                            while (running && socket === s) {
                                Thread.sleep(20_000)
                                if (!running || socket !== s) break
                                synchronized(o) { o.writeInt(0); o.flush() }
                            }
                        } catch (_: Exception) {
                        }
                    }
                    keepalive.isDaemon = true
                    keepalive.start()

                    val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
                    while (running) {
                        val len = inp.readInt()
                        if (len == 0) continue   // keepalive, not a packet
                        if (len < 0 || len > 200_000) break
                        val b = ByteArray(len); inp.readFully(b)
                        handler?.invoke(b)
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

    private companion object { const val TAG = "ServerLink" }

    override fun broadcast(bytes: ByteArray) {
        try {
            val o = out ?: return
            synchronized(o) { o.writeInt(bytes.size); o.write(bytes); o.flush() }
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
}
