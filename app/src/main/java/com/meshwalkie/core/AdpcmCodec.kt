package com.meshwalkie.core

/**
 * IMA ADPCM (4-bit) voice codec, pure Kotlin. ~4x smaller than PCM16
 * (4 bits/sample vs 16 = 8 KB/s at 16 kHz mono). Each block is self-contained
 * (carries its own initial predictor + step index) so packets decode
 * independently - robust to loss and reordering on the mesh. Lossy but fine for
 * speech. Deterministic, so it is unit-tested (unlike the MediaCodec path).
 */
object AdpcmCodec {
    private val STEP = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
        253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
        1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
        3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487,
        12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )
    private val INDEX = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)

    /** Header: predictor(2 LE) + stepIndex(1) + sampleCount(2 LE) = 5 bytes. */
    private const val HEADER = 5

    /** Encode [len] samples from [pcm] starting at [off] into a self-contained block. */
    fun encodeBlock(pcm: ShortArray, off: Int, len: Int): ByteArray {
        var predictor = pcm[off].toInt()
        var index = 0
        val out = ByteArray(HEADER + (len + 1) / 2)
        out[0] = (predictor and 0xFF).toByte()
        out[1] = ((predictor shr 8) and 0xFF).toByte()
        out[2] = index.toByte()
        out[3] = (len and 0xFF).toByte()
        out[4] = ((len shr 8) and 0xFF).toByte()

        var outPos = HEADER
        var pendingLow = false
        var lowNibble = 0
        for (i in 0 until len) {
            val sample = pcm[off + i].toInt()
            var diff = sample - predictor
            val sign = if (diff < 0) 8 else 0
            if (diff < 0) diff = -diff
            val step = STEP[index]

            var code = 0
            var temp = step
            if (diff >= temp) { code = 4; diff -= temp }
            temp = temp shr 1
            if (diff >= temp) { code = code or 2; diff -= temp }
            temp = temp shr 1
            if (diff >= temp) code = code or 1
            code = code or sign

            var diffq = step shr 3
            if (code and 4 != 0) diffq += step
            if (code and 2 != 0) diffq += step shr 1
            if (code and 1 != 0) diffq += step shr 2
            predictor += if (sign != 0) -diffq else diffq
            predictor = predictor.coerceIn(-32768, 32767)
            index = (index + INDEX[code]).coerceIn(0, 88)

            if (!pendingLow) {
                lowNibble = code and 0x0F
                pendingLow = true
            } else {
                out[outPos++] = (((code and 0x0F) shl 4) or lowNibble).toByte()
                pendingLow = false
            }
        }
        if (pendingLow) out[outPos] = lowNibble.toByte()
        return out
    }

    /** Decode a block produced by [encodeBlock] back into PCM16. */
    fun decodeBlock(block: ByteArray): ShortArray {
        var predictor = (((block[0].toInt() and 0xFF) or ((block[1].toInt() and 0xFF) shl 8))).toShort().toInt()
        var index = block[2].toInt() and 0xFF
        val len = (block[3].toInt() and 0xFF) or ((block[4].toInt() and 0xFF) shl 8)
        val out = ShortArray(len)

        var pos = HEADER
        var haveHigh = false
        var curByte = 0
        for (i in 0 until len) {
            val code: Int
            if (!haveHigh) {
                curByte = block[pos++].toInt() and 0xFF
                code = curByte and 0x0F
                haveHigh = true
            } else {
                code = (curByte shr 4) and 0x0F
                haveHigh = false
            }
            val step = STEP[index]
            var diffq = step shr 3
            if (code and 4 != 0) diffq += step
            if (code and 2 != 0) diffq += step shr 1
            if (code and 1 != 0) diffq += step shr 2
            predictor += if (code and 8 != 0) -diffq else diffq
            predictor = predictor.coerceIn(-32768, 32767)
            index = (index + INDEX[code]).coerceIn(0, 88)
            out[i] = predictor.toShort()
        }
        return out
    }
}
