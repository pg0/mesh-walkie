package com.meshwalkie.net

import android.util.Log
import com.meshwalkie.core.Transport
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app TCP relay - the "host". Binds a ServerSocket (IPv6/IPv4), accepts
 * client connections, and is a [Transport]: incoming client packets go to
 * onReceive (the engine), broadcast() length-frames bytes to every client.
 * The engine's flood/dedup does the actual relay + bridge to the BLE mesh.
 * Wire format: [4-byte big-endian length][packet bytes].
 */
class HostServer(private val port: Int) : Transport {
    private var server: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Socket>()
    @Volatile private var running = false
    @Volatile private var handler: ((ByteArray) -> Unit)? = null

    /** Current connected client count. */
    val clientCount: Int get() = clients.size

    fun start() {
        running = true
        Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                server = ss
                Log.i(TAG, "hosting on port $port")
                while (running) {
                    val c = ss.accept()
                    clients.add(c)
                    Log.i(TAG, "client connected: ${c.remoteSocketAddress} (${clients.size} total)")
                    Thread { readLoop(c) }.start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "server loop ended: $e")
            }
        }.start()
    }

    private fun readLoop(c: Socket) {
        try {
            val inp = DataInputStream(BufferedInputStream(c.getInputStream()))
            while (running) {
                val len = inp.readInt()
                if (len <= 0 || len > 200_000) break
                val b = ByteArray(len); inp.readFully(b)
                handler?.invoke(b)   // engine dedups + re-broadcasts to mesh + other clients
            }
        } catch (_: Exception) {
        } finally {
            clients.remove(c)
            try { c.close() } catch (_: Exception) {}
        }
    }

    override fun broadcast(bytes: ByteArray) {
        clients.forEach { c ->
            try {
                val out = DataOutputStream(c.getOutputStream())
                synchronized(c) { out.writeInt(bytes.size); out.write(bytes); out.flush() }
            } catch (_: Exception) {
            }
        }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) { this.handler = handler }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        clients.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        server = null
    }

    private companion object { const val TAG = "HostServer" }
}
