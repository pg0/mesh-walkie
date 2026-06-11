package com.meshwalkie.core

/**
 * Exponential low-pass for compass heading with 360-degree wraparound,
 * so the arrow glides instead of vibrating. alpha in (0,1]: higher = snappier.
 */
class HeadingFilter(private val alpha: Float = 0.15f) {
    private var filtered: Float? = null

    fun update(rawDeg: Float): Float {
        val current = filtered
        val next = if (current == null) {
            normalize(rawDeg)
        } else {
            // shortest signed angular difference in (-180, 180]
            val delta = ((rawDeg - current + 540f) % 360f) - 180f
            normalize(current + alpha * delta)
        }
        filtered = next
        return next
    }

    private fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f
}
