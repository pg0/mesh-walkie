package com.meshwalkie.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fans one engine out across several transports (BLE mesh + internet server).
 * broadcast() goes to all children; any child's incoming bytes flow to the one
 * engine handler. The engine's FloodController dedups across all of them, so a
 * packet heard on both mesh and server is processed once, and a node on both is
 * a bridge for free.
 */
class CompositeTransport(private val crypto: ChannelCrypto) : Transport {
    private val children = CopyOnWriteArrayList<Transport>()
    @Volatile private var handler: ((ByteArray) -> Unit)? = null

    fun add(t: Transport) {
        // Children carry ciphertext; decrypt before the engine sees it. Bytes
        // that fail to decrypt (wrong channel / corrupt) are dropped.
        t.onReceive { cipher ->
            val plain = try { crypto.decrypt(cipher) } catch (_: Exception) { null }
            if (plain != null) handler?.invoke(plain)
        }
        children.add(t)
    }

    fun remove(t: Transport) { children.remove(t) }

    override fun broadcast(bytes: ByteArray) {
        val cipher = crypto.encrypt(bytes)
        children.forEach { it.broadcast(cipher) }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) {
        this.handler = handler
    }
}
