package com.meshwalkie.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.meshwalkie.core.Packet
import com.meshwalkie.core.ReorderBuffer
import com.meshwalkie.core.VoiceFramer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Receives delivered Voice packets (already dedup'd by MeshEngine),
 * reorders per clip, decodes once the clip completes, plays via AudioTrack.
 * Store-and-forward per spec - playback starts at end-of-clip.
 */
class VoicePlayer(private val codec: OpusCodec = OpusCodec()) {

    // A clip is dropped if no packet for it arrives within this window, so a
    // permanently lost intermediate frame can never leak its map entry forever.
    private val CLIP_TIMEOUT_MS = 10_000L

    private class ClipState {
        val reorder = ReorderBuffer()
        var config: ByteArray? = null
        val packets = mutableListOf<ByteArray>()
        var lastFrameSeen = false
        var lastFrameNum = -1            // frameNum carried on the isLast packet
        var emittedCount = 0             // frames the buffer has emitted (incl. config frame 0)
        var lastActivityMs = System.currentTimeMillis()
    }

    private val clips = HashMap<String, ClipState>()

    /** Feed every delivered Packet.Voice here. Plays when the clip is complete. */
    @Synchronized
    fun onVoicePacket(v: Packet.Voice) {
        evictStaleClips()
        val key = "${v.originId}:${v.clipId}"
        val clip = clips.getOrPut(key) { ClipState() }
        clip.lastActivityMs = System.currentTimeMillis()
        if (v.isLast) {
            clip.lastFrameSeen = true
            clip.lastFrameNum = v.frameNum
        }
        for (frame in clip.reorder.offer(v.frameNum, v.opusData)) {
            if (clip.config == null) clip.config = frame          // frame 0 = codec config
            else clip.packets += VoiceFramer.unpack(frame)
            clip.emittedCount++
        }
        // Complete only when the last frame has been seen AND the reorder buffer
        // has contiguously emitted every frame 0..lastFrameNum (config frame included).
        if (clip.lastFrameSeen && clip.config != null &&
            clip.emittedCount == clip.lastFrameNum + 1) {
            val config = clip.config!!
            val packets = clip.packets.toList()
            clips.remove(key)
            // OpusCodec.decode() builds and releases its own MediaCodec per call, so
            // concurrent clip playback is safe even though this play thread is unsynchronized.
            Thread { play(codec.decode(config, packets)) }.start()
        }
    }

    /** Drop clips that have been idle past CLIP_TIMEOUT_MS (lost-frame leak guard). */
    private fun evictStaleClips() {
        val now = System.currentTimeMillis()
        // Per-clip state is purely in-memory (ReorderBuffer + ByteArrays); removing
        // the map entry drops the only reference, so GC reclaims it - no resource to free.
        clips.entries.removeAll { (_, clip) -> now - clip.lastActivityMs > CLIP_TIMEOUT_MS }
    }

    private fun play(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(bytes.array(), 0, bytes.capacity())
        track.play()
        // MODE_STATIC: release after playback duration
        Thread.sleep(pcm.size * 1000L / OpusCodec.SAMPLE_RATE + 200L)
        track.release()
    }
}
