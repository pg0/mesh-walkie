package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class PeakLimiterTest {

    private fun peak(pcm: ShortArray) = pcm.maxOf { abs(it.toInt()) }

    @Test
    fun neverExceedsCeilingEvenOnFullScaleInput() {
        val ceilingFrac = 0.95f
        val ceil = (ceilingFrac * Short.MAX_VALUE).toInt()
        // A run of full-scale peaks: every output sample must be clamped to the
        // ceiling (the hard clamp guarantees this even for the first transient).
        val loud = ShortArray(2000) { Short.MAX_VALUE }
        PeakLimiter(ceiling = ceilingFrac).process(loud)
        assertTrue("output peak ${peak(loud)} must stay within ceiling $ceil", peak(loud) <= ceil)
    }

    @Test
    fun leavesQuietSignalEssentiallyUntouched() {
        // Below the ceiling the envelope sits at unity, so a quiet tone passes
        // through unchanged (allowing 1 LSB of rounding).
        val quiet = ShortArray(1000) { i -> (2000 * sin(i / 8.0)).toInt().toShort() }
        val copy = quiet.copyOf()
        PeakLimiter().process(quiet)
        for (i in quiet.indices) {
            assertTrue("sample $i drifted too far", abs(quiet[i] - copy[i]) <= 1)
        }
    }

    @Test
    fun settlesLoudSteadyToneToTheCeiling() {
        val ceilingFrac = 0.9f
        val ceil = (ceilingFrac * Short.MAX_VALUE).toInt()
        // A steady tone well above the ceiling: after the attack settles, the
        // sustained peaks should sit right around the ceiling, not far under it.
        val amp = 30000
        val loud = ShortArray(4000) { i -> (amp * sin(i / 4.0)).toInt().toShort() }
        PeakLimiter(ceiling = ceilingFrac).process(loud)
        val tailPeak = peak(loud.copyOfRange(2000, 4000))
        assertTrue("tail peak $tailPeak should be limited under ceiling $ceil", tailPeak <= ceil)
        assertTrue("tail peak $tailPeak should be reasonably near ceiling", tailPeak >= ceil * 0.7)
    }

    @Test
    fun envelopePersistsAcrossChunks() {
        // Streaming the same loud signal chunk-by-chunk must limit identically
        // to processing it whole (shared gain state, no per-chunk reset click).
        val whole = ShortArray(3000) { Short.MAX_VALUE }
        val chunked = whole.copyOf()
        PeakLimiter().process(whole)

        val lim = PeakLimiter()
        var i = 0
        while (i < chunked.size) {
            val end = minOf(i + 500, chunked.size)
            val part = chunked.copyOfRange(i, end)
            lim.process(part)
            part.copyInto(chunked, i)
            i = end
        }
        assertTrue("chunked and whole peaks must match", peak(whole) == peak(chunked))
    }

    @Test
    fun emptyInputIsSafe() {
        assertEquals(0, PeakLimiter().process(ShortArray(0)).size)
    }
}
