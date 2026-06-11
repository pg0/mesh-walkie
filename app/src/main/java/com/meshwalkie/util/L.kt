package com.meshwalkie.util

import android.util.Log

/**
 * Single switch for all app logging. OFF by default so no IPs, endpoint names,
 * or connection details leak to logcat in normal use. Flip [ON] to true only
 * for debugging.
 */
object L {
    const val ON = false

    fun i(tag: String, msg: String) { if (ON) Log.i(tag, msg) }
    fun w(tag: String, msg: String) { if (ON) Log.w(tag, msg) }
    fun w(tag: String, msg: String, e: Throwable) { if (ON) Log.w(tag, msg, e) }
    fun e(tag: String, msg: String) { if (ON) Log.e(tag, msg) }
}
