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

    private class ClipState {
        val reorder = ReorderBuffer()
        var config: ByteArray? = null
        val packets = mutableListOf<ByteArray>()
        var lastFrameSeen = false
    }

    private val clips = HashMap<String, ClipState>()

    /** Feed every delivered Packet.Voice here. Plays when the clip is complete. */
    @Synchronized
    fun onVoicePacket(v: Packet.Voice) {
        val key = "${v.originId}:${v.clipId}"
        val clip = clips.getOrPut(key) { ClipState() }
        if (v.isLast) clip.lastFrameSeen = true
        for (frame in clip.reorder.offer(v.frameNum, v.opusData)) {
            if (clip.config == null) clip.config = frame          // frame 0 = codec config
            else clip.packets += VoiceFramer.unpack(frame)
        }
        if (clip.lastFrameSeen && clip.config != null) {
            val config = clip.config!!
            val packets = clip.packets.toList()
            clips.remove(key)
            Thread { play(codec.decode(config, packets)) }.start()
        }
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
