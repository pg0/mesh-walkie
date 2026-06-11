package com.meshwalkie.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * Voice-activated transmit. Runs a continuous mic loop: when frame energy (RMS)
 * rises above [threshold] it starts a clip, and after [hangoverMs] of silence it
 * emits the clip via [onClip]. Hands-free alternative to push-to-talk.
 *
 * Blocking - run on a background coroutine. Call [stop] to end the loop.
 */
class VadRecorder {

    @Volatile private var running = false

    /** Map sensitivity 0..100 to an RMS energy threshold (higher sens = lower threshold). */
    fun thresholdFor(sensitivity: Int): Double {
        val s = sensitivity.coerceIn(0, 100) / 100.0
        // sens 0 -> ~3000 (only loud speech), sens 100 -> ~250 (very sensitive)
        return 3000.0 - s * 2750.0
    }

    @SuppressLint("MissingPermission")
    fun run(threshold: Double, onClip: (ShortArray) -> Unit) {
        running = true
        val minBuf = AudioRecord.getMinBufferSize(
            OpusCodec.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, OpusCodec.FRAME_SAMPLES * 2 * 4)
        )
        val frame = ShortArray(OpusCodec.FRAME_SAMPLES)
        val clip = ArrayList<Short>()
        var speaking = false
        var silenceMs = 0
        val hangoverMs = 800
        val maxSamples = OpusCodec.SAMPLE_RATE * 15
        try {
            recorder.startRecording()
            while (running) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) continue
                val energy = rms(frame, read)
                if (energy >= threshold) {
                    if (!speaking) { speaking = true; clip.clear() }
                    silenceMs = 0
                    for (i in 0 until read) clip += frame[i]
                } else if (speaking) {
                    for (i in 0 until read) clip += frame[i]   // keep the word's tail
                    silenceMs += read * 1000 / OpusCodec.SAMPLE_RATE
                    if (silenceMs >= hangoverMs) {
                        speaking = false
                        if (clip.size > OpusCodec.FRAME_SAMPLES * 8) onClip(clip.toShortArray())
                        clip.clear()
                    }
                }
                if (clip.size >= maxSamples) {
                    speaking = false
                    onClip(clip.toShortArray()); clip.clear()
                }
            }
            if (speaking && clip.size > OpusCodec.FRAME_SAMPLES * 8) onClip(clip.toShortArray())
        } finally {
            recorder.stop(); recorder.release()
        }
    }

    fun stop() { running = false }

    private fun rms(buf: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
        return sqrt(sum / n)
    }
}
