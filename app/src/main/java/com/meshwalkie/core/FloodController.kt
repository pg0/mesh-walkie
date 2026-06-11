package com.meshwalkie.core

/**
 * Controlled flooding: dedup via SeenSet, TTL decrement.
 * Seen -> drop. New + ttl>0 -> deliver and forward ttl-1.
 * New + ttl==0 -> deliver only.
 */
class FloodController(private val seen: SeenSet = SeenSet()) {

    data class Result(val deliver: Boolean, val forward: Packet?) {
        companion object { val DROP = Result(deliver = false, forward = null) }
    }

    fun onReceive(packet: Packet): Result {
        if (!seen.checkAndAdd(packet.dedupKey)) return Result.DROP
        val forward = if (packet.ttl > 0) packet.withTtl(packet.ttl - 1) else null
        return Result(deliver = true, forward = forward)
    }

    /** Mark a locally originated packet so echoes from neighbors are dropped. */
    fun markOwn(packet: Packet) {
        seen.checkAndAdd(packet.dedupKey)
    }
}
