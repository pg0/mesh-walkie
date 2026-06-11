package com.meshwalkie.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gapless playback for live mode. Keeps one streaming AudioTrack open and writes
 * decoded PCM chunks into it as they arrive; the track's own buffer is the jitter
 * buffer. Blocking writes pace playback at the sample rate. The track is opened
 * lazily on the first chunk and released after [IDLE_CLOSE_MS] with no data, so a
 * finished live stream stops cleanly without an explicit end signal.
 */
class StreamPlayer {

    private var track: AudioTrack? = null
    private var trackEarpiece = false        // routing the open track was built for
    @Volatile private var lastWriteMs = 0L
    @Volatile private var watchdog: Thread? = null
    @Volatile var muted = false

    @Synchronized
    fun write(pcm: ShortArray) {
        if (pcm.isEmpty() || muted) return
        if (track != null && trackEarpiece != AudioRoute.earpiece) close()  // route changed -> rebuild
        val t = track ?: open().also { track = it; trackEarpiece = AudioRoute.earpiece }
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        t.write(bytes.array(), 0, bytes.capacity(), AudioTrack.WRITE_BLOCKING)
        lastWriteMs = System.currentTimeMillis()
    }

    private fun open(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            OpusCodec.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val usage = if (AudioRoute.earpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION
        else AudioAttributes.USAGE_MEDIA
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OpusCodec.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            // ~0.5 s buffer absorbs mesh jitter between chunks.
            .setBufferSizeInBytes(maxOf(minBuf, OpusCodec.SAMPLE_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        startWatchdog()
        return t
    }

    private fun startWatchdog() {
        if (watchdog != null) return
        val w = Thread {
            try {
                while (true) {
                    Thread.sleep(500)
                    val idle = System.currentTimeMillis() - lastWriteMs
                    if (idle > IDLE_CLOSE_MS) { close(); break }
                }
            } catch (_: InterruptedException) {
            }
        }
        watchdog = w
        w.isDaemon = true
        w.start()
    }

    @Synchronized
    fun close() {
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
        watchdog = null
    }

    private companion object {
        const val IDLE_CLOSE_MS = 1500L   // no live chunk for this long = stream ended
    }
}
