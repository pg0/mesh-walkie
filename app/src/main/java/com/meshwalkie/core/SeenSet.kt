package com.meshwalkie.core

/**
 * Bounded dedup memory: LRU ~500 entries keyed by Packet.dedupKey,
 * insertion-order eviction (oldest first). Thread-safe via synchronization
 * because transports deliver from binder/radio threads.
 */
class SeenSet(private val capacity: Int = 500) {
    private val map = object : LinkedHashMap<String, Unit>(capacity, 0.75f, false) {
        // Kotlin override of java.util.LinkedHashMap's protected eviction hook
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > capacity
    }

    /** @return true if the key was NOT seen before (i.e. packet is new). */
    @Synchronized
    fun checkAndAdd(key: String): Boolean {
        if (map.containsKey(key)) return false
        map[key] = Unit
        return true
    }

    val size: Int @Synchronized get() = map.size
}
