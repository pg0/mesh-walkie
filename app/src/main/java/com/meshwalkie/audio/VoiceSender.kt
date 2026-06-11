package com.meshwalkie.audio

import com.meshwalkie.core.MeshEngine
import com.meshwalkie.core.Packet
import com.meshwalkie.core.VoiceFramer

/**
 * Clip -> Opus -> 1 s frames -> Voice packets onto the mesh.
 * Frame 0 = codec config alone; frames 1..n = packed Opus; last flagged isLast.
 */
class VoiceSender(
    private val engine: MeshEngine,
    private val originId: String,
    private val nextSeq: () -> Int,
    private val codec: OpusCodec = OpusCodec()
) {
    private var clipCounter = 0

    /** @return the clipId assigned to this clip (for delivery-receipt tracking). */
    fun sendClip(
        pcm: ShortArray,
        nowMs: Long,
        bitrate: Int = OpusCodec.DEFAULT_BITRATE,
        live: Boolean = false
    ): Int {
        if (pcm.isEmpty()) return -1
        val clipId = clipCounter++
        val encoded = codec.encode(pcm, bitrate)

        val frames = mutableListOf<ByteArray>()
        // 1 s frames: ADPCM is ~165 B / 20 ms packet, so a frame is ~8 KB,
        // well under the Nearby BYTES payload limit (32 KB).
        val framer = VoiceFramer(frameDurationMs = 1000)
        encoded.packets.forEach { packet -> framer.add(packet)?.let { frames += it } }
        framer.flush()?.let { frames += it }

        var frameNum = 0
        fun emit(data: ByteArray, isLast: Boolean) {
            engine.send(
                Packet.Voice(
                    originId = originId, seqNum = nextSeq(), ttl = Packet.DEFAULT_TTL,
                    timestampMs = nowMs, clipId = clipId, frameNum = frameNum++,
                    isLast = isLast, opusData = data, live = live
                )
            )
        }
        emit(encoded.config, isLast = frames.isEmpty())
        frames.forEachIndexed { i, frame -> emit(frame, isLast = i == frames.lastIndex) }
        return clipId
    }
}
