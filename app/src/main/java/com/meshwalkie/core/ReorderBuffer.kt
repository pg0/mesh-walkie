package com.meshwalkie.core

/**
 * Per-clip frame reordering: voice frames may arrive out of order over
 * multi-path flooding. offer() returns every frame now ready to play, in order.
 */
class ReorderBuffer {
    private var nextFrame = 0
    private val pending = sortedMapOf<Int, ByteArray>()

    fun offer(frameNum: Int, data: ByteArray): List<ByteArray> {
        if (frameNum < nextFrame || pending.containsKey(frameNum)) return emptyList()
        pending[frameNum] = data
        val ready = mutableListOf<ByteArray>()
        while (pending.containsKey(nextFrame)) {
            ready += pending.remove(nextFrame)!!
            nextFrame++
        }
        return ready
    }
}
