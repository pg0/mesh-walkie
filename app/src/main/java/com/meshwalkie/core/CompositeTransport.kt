package com.meshwalkie.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fans one engine out across several transports (BLE mesh + internet server).
 * broadcast() goes to all children; any child's incoming bytes flow to the one
 * engine handler. The engine's FloodController dedups across all of them, so a
 * packet heard on both mesh and server is processed once, and a node on both is
 * a bridge for free.
 *
 * Each child is tagged with the [Route] it represents so the engine can label
 * where a peer/packet was heard (BLE mesh vs internet). The tag rides alongside
 * the decrypted bytes and does not affect dedup or delivery.
 */
class CompositeTransport(private val crypto: ChannelCrypto) : RoutedTransport {
    private val children = CopyOnWriteArrayList<Pair<Transport, Route>>()
    @Volatile private var handler: ((ByteArray, Route) -> Unit)? = null

    fun add(t: Transport, route: Route) {
        // Children carry ciphertext; decrypt before the engine sees it. Bytes
        // that fail to decrypt (wrong channel / corrupt) are dropped.
        t.onReceive { cipher ->
            val plain = try { crypto.decrypt(cipher) } catch (_: Exception) { null }
            if (plain != null) handler?.invoke(plain, route)
        }
        children.add(t to route)
    }

    fun remove(t: Transport) { children.removeAll { it.first === t } }

    override fun broadcast(bytes: ByteArray) {
        val cipher = crypto.encrypt(bytes)
        children.forEach { it.first.broadcast(cipher) }
    }

    /** Legacy single-arg intake: routes are collapsed to their bytes. */
    override fun onReceive(handler: (ByteArray) -> Unit) {
        this.handler = { bytes, _ -> handler(bytes) }
    }

    override fun onReceiveRouted(handler: (ByteArray, Route) -> Unit) {
        this.handler = handler
    }
}
