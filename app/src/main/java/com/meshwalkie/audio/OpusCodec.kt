package com.meshwalkie.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.meshwalkie.core.AdpcmCodec
import java.nio.ByteOrder

/**
 * Voice codec for the PTT pipeline.
 *
 * Primary: AMR-WB (wideband telephone codec) via MediaCodec - speech-optimised,
 * no quantisation hiss, no fragile csd headers (unlike Opus). Falls back to
 * pure-Kotlin IMA ADPCM if a device lacks the AMR-WB codec. The first config
 * byte tags which codec produced the packets, so mixed devices interoperate and
 * the fallback is safe.
 *
 * 16 kHz mono, 20 ms (FRAME_SAMPLES) frames. Encoded(config, packets): config[0]
 * = codec id; packets = AMR-WB frames or independent ADPCM blocks.
 */
class OpusCodec {

    data class Encoded(val config: ByteArray, val packets: List<ByteArray>)

    fun encode(pcm: ShortArray): Encoded =
        try {
            Encoded(byteArrayOf(CODEC_AMR), amrEncode(pcm))
        } catch (e: Exception) {
            Encoded(byteArrayOf(CODEC_ADPCM), adpcmEncode(pcm))
        }

    fun decode(config: ByteArray, packets: List<ByteArray>): ShortArray {
        val codec = if (config.isNotEmpty()) config[0] else CODEC_ADPCM
        return try {
            if (codec == CODEC_AMR) amrDecode(packets) else adpcmDecode(packets)
        } catch (e: Exception) {
            ShortArray(0)
        }
    }

    // --- AMR-WB via MediaCodec -------------------------------------------------

    private fun amrEncode(pcm: ShortArray): List<ByteArray> {
        val codec = MediaCodec.createEncoderByType(AMR_WB)
        val format = MediaFormat.createAudioFormat(AMR_WB, SAMPLE_RATE, CHANNELS)
            .apply { setInteger(MediaFormat.KEY_BIT_RATE, AMR_BITRATE) }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val out = mutableListOf<ByteArray>()
        val info = MediaCodec.BufferInfo()
        var inOffset = 0
        var inputDone = false
        var stalls = 0
        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!.apply { clear() }
                        val samples = minOf(FRAME_SAMPLES, pcm.size - inOffset)
                        if (samples <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            inBuf.order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until samples) inBuf.putShort(pcm[inOffset + i])
                            val ptUs = inOffset.toLong() * 1_000_000L / SAMPLE_RATE
                            codec.queueInputBuffer(inIdx, 0, samples * 2, ptUs, 0)
                            inOffset += samples
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    stalls = 0
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        out += ByteArray(info.size).also { buf.get(it) }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (inputDone && ++stalls > MAX_STALLS) {
                    throw IllegalStateException("amr encode drained without EOS")
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        return out
    }

    private fun amrDecode(frames: List<ByteArray>): ShortArray {
        val codec = MediaCodec.createDecoderByType(AMR_WB)
        val format = MediaFormat.createAudioFormat(AMR_WB, SAMPLE_RATE, CHANNELS)
        codec.configure(format, null, null, 0)
        codec.start()
        val pcm = ArrayList<Short>(frames.size * FRAME_SAMPLES)
        val info = MediaCodec.BufferInfo()
        var idx = 0
        var inputDone = false
        var stalls = 0
        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        if (idx >= frames.size) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val f = frames[idx++]
                            codec.getInputBuffer(inIdx)!!.apply { clear(); put(f) }
                            codec.queueInputBuffer(inIdx, 0, f.size, 0, 0)
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    stalls = 0
                    val buf = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    repeat(info.size / 2) { pcm += buf.short }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (inputDone && ++stalls > MAX_STALLS) {
                    throw IllegalStateException("amr decode drained without EOS")
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        return pcm.toShortArray()
    }

    // --- ADPCM fallback (pure Kotlin) -----------------------------------------

    private fun adpcmEncode(pcm: ShortArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val n = minOf(FRAME_SAMPLES, pcm.size - offset)
            packets += AdpcmCodec.encodeBlock(pcm, offset, n)
            offset += n
        }
        return packets
    }

    private fun adpcmDecode(packets: List<ByteArray>): ShortArray {
        val chunks = packets.map { AdpcmCodec.decodeBlock(it) }
        val out = ShortArray(chunks.sumOf { it.size })
        var idx = 0
        for (c in chunks) { System.arraycopy(c, 0, out, idx, c.size); idx += c.size }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val FRAME_SAMPLES = 320      // 20 ms at 16 kHz
        private const val AMR_WB = "audio/amr-wb"
        private const val AMR_BITRATE = 23_850   // highest AMR-WB mode
        private const val CODEC_AMR: Byte = 1
        private const val CODEC_ADPCM: Byte = 2
        private const val TIMEOUT_US = 10_000L
        private const val MAX_STALLS = 1000
    }
}
