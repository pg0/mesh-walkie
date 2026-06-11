package com.meshwalkie.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Synchronous MediaCodec Opus wrapper. 16 kHz mono, 20 ms packets
 * (320 samples) - matches VoiceFramer's packetDurationMs = 20.
 */
class OpusCodec {

    data class Encoded(val config: ByteArray, val packets: List<ByteArray>)

    /** PCM16 -> Opus packets + the codec-config (identification header). */
    fun encode(pcm: ShortArray): Encoded {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS
        ).apply { setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE) }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var config = ByteArray(0)
        val packets = mutableListOf<ByteArray>()
        var inOffset = 0
        var inputDone = false
        // Guard against an infinite spin if EOS never arrives once input is done.
        var stalls = 0
        val info = MediaCodec.BufferInfo()

        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!.apply { clear() }
                        val samples = minOf(FRAME_SAMPLES, pcm.size - inOffset)
                        if (samples <= 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            inBuf.order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until samples) inBuf.putShort(pcm[inOffset + i])
                            inOffset += samples
                            codec.queueInputBuffer(inIdx, 0, samples * 2, 0, 0)
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    stalls = 0
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val out = ByteArray(info.size)
                    outBuf.get(out)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        config = out
                    } else if (info.size > 0) {
                        packets += out
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (inputDone && ++stalls > MAX_DRAIN_STALLS) {
                    throw IllegalStateException("opus codec drained without EOS")
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        return Encoded(config, packets)
    }

    /** Opus packets -> PCM16. config = csd-0 captured by encode(). */
    fun decode(config: ByteArray, packets: List<ByteArray>): ShortArray {
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val zeros64 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(0L)
            .apply { flip() }
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS
        ).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(config))
            setByteBuffer("csd-1", zeros64.duplicate())  // pre-skip ns = 0
            setByteBuffer("csd-2", zeros64.duplicate())  // seek pre-roll ns = 0
        }
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = mutableListOf<Short>()
        var packetIdx = 0
        var inputDone = false
        // Guard against an infinite spin if EOS never arrives once input is done.
        var stalls = 0
        val info = MediaCodec.BufferInfo()

        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        if (packetIdx >= packets.size) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val packet = packets[packetIdx++]
                            codec.getInputBuffer(inIdx)!!.apply { clear(); put(packet) }
                            codec.queueInputBuffer(inIdx, 0, packet.size, 0, 0)
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    stalls = 0
                    val buf = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    repeat(info.size / 2) { pcmOut += buf.short }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (inputDone && ++stalls > MAX_DRAIN_STALLS) {
                    throw IllegalStateException("opus codec drained without EOS")
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        return pcmOut.toShortArray()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val FRAME_SAMPLES = 320      // 20 ms at 16 kHz
        const val BIT_RATE = 24_000
        private const val TIMEOUT_US = 10_000L
        // Cap on consecutive INFO_TRY_AGAIN_LATER after input EOS submitted (~10 s at 10 ms timeout).
        private const val MAX_DRAIN_STALLS = 1000
    }
}
