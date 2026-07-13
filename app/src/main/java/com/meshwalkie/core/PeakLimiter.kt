package com.meshwalkie.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Feed-forward brick-wall peak limiter for PCM16, pure Kotlin so it is device
 * independent and unit-testable. Keeps the signal from clipping when the mic
 * (or the device AGC ahead of it) pushes peaks toward full scale: a smoothed
 * gain envelope ducks fast on a peak and releases slowly to avoid pumping, and
 * a final hard clamp to [ceiling] guarantees no sample ever exceeds it - even
 * the one-sample transient before the envelope reacts (no look-ahead).
 *
 * Stateful: [process] carries the gain envelope across calls, so streaming a
 * clip chunk-by-chunk (live mode) limits identically to processing it whole.
 * One instance per capture session; not thread-safe.
 */
class PeakLimiter(
    ceiling: Float = 0.95f,        // fraction of full scale the output may reach
    private val attack: Float = 0.4f,      // gain drop toward target per sample on a peak
    private val release: Float = 0.0006f   // gain recovery per sample when clear
) {
    private val ceil: Float = (ceiling.coerceIn(0.1f, 1f)) * Short.MAX_VALUE
    private val ceilInt: Int = ceil.toInt()   // floor, so output never exceeds the ceiling
    private var gain = 1f

    /** Limit peaks in [pcm] in place, returning the same array for chaining. */
    fun process(pcm: ShortArray): ShortArray {
        for (i in pcm.indices) {
            val raw = pcm[i].toInt()
            val mag = abs(raw)
            // Gain that would bring this sample exactly to the ceiling (1.0 if
            // it is already under). Envelope chases it: fast down, slow up.
            val target = if (mag * gain > ceil) ceil / mag.coerceAtLeast(1) else 1f
            gain += if (target < gain) (target - gain) * attack
                    else (target - gain) * release
            if (gain > 1f) gain = 1f
            val out = (raw * gain).roundToInt().coerceIn(-ceilInt, ceilInt)
            pcm[i] = out.toShort()
        }
        return pcm
    }
}
