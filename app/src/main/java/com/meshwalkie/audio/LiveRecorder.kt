package com.meshwalkie.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Continuous mic capture for live mode. Unlike [PttRecorder] (one clip per hold)
 * this loops, emitting a fixed-size PCM chunk every [CHUNK_MS] until [stop], so
 * each chunk can be encoded and streamed immediately - the receiver plays them
 * back-to-back via a streaming AudioTrack. Blocking; run on an IO thread.
 */
class LiveRecorder {

    @Volatile private var running = false

    /** Caller (MeshService) holds RECORD_AUDIO before calling. */
    @SuppressLint("MissingPermission")
    fun run(audioSource: Int = MediaRecorder.AudioSource.MIC, onChunk: (ShortArray) -> Unit) {
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
        val frame = ShortArray(OpusCodec.FRAME_SAMPLES)
        try {
            recorder.startRecording()
            val chunk = ArrayList<Short>(CHUNK_SAMPLES)
            while (running) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) continue
                for (i in 0 until read) chunk += frame[i]
                if (chunk.size >= CHUNK_SAMPLES) {
                    onChunk(chunk.toShortArray())
                    chunk.clear()
                }
            }
            if (chunk.isNotEmpty()) onChunk(chunk.toShortArray())   // flush tail
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    fun stop() { running = false }

    private companion object {
        const val CHUNK_MS = 240
        const val CHUNK_SAMPLES = OpusCodec.SAMPLE_RATE * CHUNK_MS / 1000   // 3840 @ 16 kHz
    }
}
