package com.meshwalkie.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Captures PCM16 mono 16 kHz from the mic while PTT is held.
 * Blocking - run on a background thread/dispatcher.
 */
class PttRecorder {

    private companion object {
        const val TAIL_MS = 300   // extra capture after release to keep the word's tail
    }

    /** Caller (MeshService) holds RECORD_AUDIO before calling. */
    @SuppressLint("MissingPermission")
    fun record(isHeld: () -> Boolean, maxMs: Int = 15_000): ShortArray {
        val minBuf = AudioRecord.getMinBufferSize(
            OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, OpusCodec.FRAME_SAMPLES * 2 * 4)
        )
        val maxSamples = OpusCodec.SAMPLE_RATE * maxMs / 1000
        val pcm = ArrayList<Short>(maxSamples)
        val chunk = ShortArray(OpusCodec.FRAME_SAMPLES)
        try {
            recorder.startRecording()
            while (isHeld() && pcm.size < maxSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                for (i in 0 until read) pcm += chunk[i]
            }
            // Tail grace: on release the mic hardware buffer still holds the last
            // ~100-300 ms of speech. Drain it so the end of the word is not cut.
            val tailSamples = OpusCodec.SAMPLE_RATE * TAIL_MS / 1000
            var drained = 0
            while (drained < tailSamples && pcm.size < maxSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) break
                for (i in 0 until read) pcm += chunk[i]
                drained += read
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        return pcm.toShortArray()
    }
}
