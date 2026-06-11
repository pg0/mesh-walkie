package com.meshwalkie.audio

import com.meshwalkie.core.AdpcmCodec

/**
 * Voice codec for the PTT pipeline.
 *
 * Uses IMA ADPCM (4-bit, ~8 KB/s at 16 kHz) rather than Opus: the MediaCodec
 * Opus path decoded to noise (the Android Opus csd-0/1/2 handshake is fragile),
 * and ADPCM is pure-Kotlin, robust, ~4x smaller than raw PCM, and unit-tested.
 * Each FRAME_SAMPLES chunk is an independent ADPCM block, so packets survive
 * loss/reordering on the mesh. The Encoded(config, packets) shape is preserved
 * (config is empty) so the framer, reorder buffer, sender and player are
 * unchanged - only the bytes differ. Opus remains a future bandwidth option.
 */
class OpusCodec {

    data class Encoded(val config: ByteArray, val packets: List<ByteArray>)

    /** PCM16 -> independent ADPCM blocks of FRAME_SAMPLES. config is empty. */
    fun encode(pcm: ShortArray): Encoded {
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val n = minOf(FRAME_SAMPLES, pcm.size - offset)
            packets += AdpcmCodec.encodeBlock(pcm, offset, n)
            offset += n
        }
        return Encoded(ByteArray(0), packets)
    }

    /** ADPCM blocks -> PCM16. config is ignored (empty). */
    fun decode(config: ByteArray, packets: List<ByteArray>): ShortArray {
        val chunks = packets.map { AdpcmCodec.decodeBlock(it) }
        val out = ShortArray(chunks.sumOf { it.size })
        var idx = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, idx, c.size)
            idx += c.size
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val FRAME_SAMPLES = 320      // 20 ms at 16 kHz
    }
}
