package com.meshwalkie.core

/**
 * In-memory transport. Linked fakes deliver synchronously - a broadcast
 * recursively triggers neighbor handlers, so loops are real and only
 * dedup/TTL stop them. That is exactly what we want to prove.
 */
class FakeTransport : Transport {
    private val links = mutableListOf<FakeTransport>()
    private var handler: ((ByteArray) -> Unit)? = null

    override fun broadcast(bytes: ByteArray) {
        links.forEach { it.handler?.invoke(bytes) }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) {
        this.handler = handler
    }

    companion object {
        fun link(a: FakeTransport, b: FakeTransport) {
            a.links += b
            b.links += a
        }
    }
}
