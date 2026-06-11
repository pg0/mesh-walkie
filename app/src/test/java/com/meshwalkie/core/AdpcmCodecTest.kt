package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Test

class AdpcmCodecTest {

    private fun sine(n: Int, freq: Double, amp: Double = 8000.0): ShortArray =
        ShortArray(n) { (amp * sin(2 * PI * freq * it / 16000.0)).toInt().toShort() }

    @Test
    fun roundTripPreservesLength() {
        val pcm = sine(320, 300.0)
        val block = AdpcmCodec.encodeBlock(pcm, 0, pcm.size)
        val out = AdpcmCodec.decodeBlock(block)
        assertEquals(320, out.size)
    }

    @Test
    fun compressionIsAboutFourToOne() {
        val pcm = sine(320, 300.0)
        val block = AdpcmCodec.encodeBlock(pcm, 0, pcm.size)
        // 320 samples * 2 bytes = 640 PCM bytes; ADPCM = 5 header + 160 nibbles = 165.
        assertEquals(165, block.size)
        assertTrue("ADPCM should be ~4x smaller", block.size < pcm.size * 2 / 3)
    }

    @Test
    fun roundTripReconstructsSpeechBandSignalWithinTolerance() {
        // A 300 Hz tone is well within ADPCM's tracking ability; RMS error should
        // be a small fraction of the signal amplitude.
        val pcm = sine(2000, 300.0, amp = 8000.0)
        val block = AdpcmCodec.encodeBlock(pcm, 0, pcm.size)
        val out = AdpcmCodec.decodeBlock(block)
        var sumSqErr = 0.0
        var sumSqSig = 0.0
        for (i in pcm.indices) {
            val e = (pcm[i] - out[i]).toDouble()
            sumSqErr += e * e
            sumSqSig += pcm[i].toDouble() * pcm[i]
        }
        val rmsErr = sqrt(sumSqErr / pcm.size)
        val rmsSig = sqrt(sumSqSig / pcm.size)
        // Error energy well below signal energy (better than 6 dB SNR by a wide margin).
        assertTrue("rmsErr=$rmsErr rmsSig=$rmsSig", rmsErr < rmsSig * 0.25)
    }

    @Test
    fun firstSampleIsTrackedFromHeaderPredictor() {
        val pcm = shortArrayOf(1234, 1200, 1100, 1000, 900, 800, 700, 600)
        val block = AdpcmCodec.encodeBlock(pcm, 0, pcm.size)
        val out = AdpcmCodec.decodeBlock(block)
        // First decoded sample equals the header predictor exactly.
        assertEquals(1234, out[0].toInt())
    }

    @Test
    fun oddSampleCountRoundTrips() {
        val pcm = sine(321, 250.0)
        val block = AdpcmCodec.encodeBlock(pcm, 0, pcm.size)
        val out = AdpcmCodec.decodeBlock(block)
        assertEquals(321, out.size)
    }
}
