package com.meshwalkie.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.meshwalkie.core.PeakLimiter
import kotlin.math.abs

/**
 * Continuous mic capture for live mode. Unlike [PttRecorder] (one clip per hold)
 * this loops, emitting a fixed-size PCM chunk every [CHUNK_MS] until [stop], so
 * each chunk can be encoded and streamed immediately - the receiver plays them
 * back-to-back via a streaming AudioTrack. Blocking; run on an IO thread.
 *
 * With [voiceOnly] it gates on speech: silent chunks are dropped (no constant
 * room-noise stream), but a short hangover keeps the tail of each phrase.
 */
class LiveRecorder {

    @Volatile private var running = false

    /**
     * Caller (MeshService) holds RECORD_AUDIO before calling. With [agc] the
     * device auto-gain is attached to the capture session and a peak limiter
     * runs on each chunk (one shared envelope across chunks) so the live stream
     * stays level and un-clipped.
     */
    @SuppressLint("MissingPermission")
    fun run(
        audioSource: Int = MediaRecorder.AudioSource.MIC,
        voiceOnly: Boolean = false,
        agc: Boolean = false,
        onChunk: (ShortArray) -> Unit
    ) {
        running = true
        val minBuf = AudioRecord.getMinBufferSize(
            OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            audioSource,
            OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_SAMPLES * 2 * 2)
        )
        val gainControl = CaptureAgc.attach(recorder, agc)
        val limiter = if (agc) PeakLimiter() else null
        val frame = ShortArray(OpusCodec.FRAME_SAMPLES)
        var hangover = 0
        try {
            recorder.startRecording()
            val chunk = ArrayList<Short>(CHUNK_SAMPLES)
            while (running) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) continue
                for (i in 0 until read) chunk += frame[i]
                if (chunk.size >= CHUNK_SAMPLES) {
                    val arr = chunk.toShortArray().also { limiter?.process(it) }
                    val speech = isSpeech(arr)
                    if (speech) hangover = HANGOVER_CHUNKS
                    if (!voiceOnly || speech || hangover > 0) {
                        onChunk(arr)
                        if (!speech && hangover > 0) hangover--
                    }
                    chunk.clear()
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            gainControl?.release()
        }
    }

    private fun isSpeech(pcm: ShortArray): Boolean {
        var sum = 0L
        for (s in pcm) sum += abs(s.toInt())
        return pcm.isNotEmpty() && sum / pcm.size > SPEECH_THRESHOLD
    }

    fun stop() { running = false }

    private companion object {
        const val CHUNK_MS = 240
        const val CHUNK_SAMPLES = OpusCodec.SAMPLE_RATE * CHUNK_MS / 1000   // 3840 @ 16 kHz
        const val SPEECH_THRESHOLD = 300    // mean abs amplitude; below = silence/room
        const val HANGOVER_CHUNKS = 3       // keep streaming ~0.7s after last speech
    }
}
