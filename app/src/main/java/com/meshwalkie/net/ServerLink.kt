package com.meshwalkie.net

import com.meshwalkie.core.Transport
import com.meshwalkie.util.L
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Client side of the internet relay - connects to a [HostServer] at [host]:[port]
 * over the internet (IPv6 direct, no NAT). A [Transport]: broadcast() sends to
 * the host, onReceive() delivers packets the host relays. Same length-framed
 * wire format as HostServer.
 */
class ServerLink(private val host: String, private val port: Int) : Transport {
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    @Volatile private var running = false
    @Volatile private var handler: ((ByteArray) -> Unit)? = null
    @Volatile var onState: ((connected: Boolean) -> Unit)? = null

    fun connect() {
        running = true
        Thread {
            try {
                L.i(TAG, "connecting to [$host]:$port")
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 8000)
                socket = s
                out = DataOutputStream(s.getOutputStream())
                L.i(TAG, "connected to [$host]:$port")
                onState?.invoke(true)
                val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
                while (running) {
                    val len = inp.readInt()
                    if (len <= 0 || len > 200_000) break
                    val b = ByteArray(len); inp.readFully(b)
                    handler?.invoke(b)
                }
            } catch (e: Exception) {
                L.w(TAG, "connect/read failed for [$host]:$port: $e")
            } finally {
                onState?.invoke(false)
                close()
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

    fun close() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null; out = null
    }
}
