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
        } finally {
            recorder.stop()
            recorder.release()
        }
        return pcm.toShortArray()
    }
}
