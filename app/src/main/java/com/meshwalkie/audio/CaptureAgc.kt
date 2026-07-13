package com.meshwalkie.audio

import android.media.AudioRecord
import android.media.audiofx.AutomaticGainControl
import com.meshwalkie.util.L

/**
 * Android's built-in capture-side Automatic Gain Control. Attaching it to an
 * [AudioRecord] session levels the mic so a near/far/quiet/loud speaker is
 * transmitted at a consistent volume - done once at the source, so every
 * receiver benefits. AGC alone can push peaks into clipping, so a
 * [com.meshwalkie.core.PeakLimiter] runs on the captured PCM as well.
 *
 * Not every device exposes the effect; [attach] returns null when unavailable
 * or disabled, and the caller must [AutomaticGainControl.release] it with the
 * recorder.
 */
object CaptureAgc {
    private const val TAG = "CaptureAgc"

    fun attach(record: AudioRecord, enabled: Boolean): AutomaticGainControl? {
        if (!enabled) return null
        if (!AutomaticGainControl.isAvailable()) {
            L.i(TAG, "AGC not available on this device")
            return null
        }
        return try {
            AutomaticGainControl.create(record.audioSessionId)?.apply { setEnabled(true) }
        } catch (e: Exception) {
            L.w(TAG, "AGC attach failed: $e")
            null
        }
    }
}
