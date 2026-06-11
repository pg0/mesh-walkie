package com.meshwalkie.service

import android.content.Context
import android.os.Build

/** Stable random id + human name, persisted across launches. */
object DeviceId {
    private const val PREFS = "meshwalkie"
    private const val KEY_ID = "device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.let { return it }
        val id = List(8) { "0123456789abcdef".random() }.joinToString("")
        prefs.edit().putString(KEY_ID, id).apply()
        return id
    }

    fun displayName(context: Context): String = "${Build.MODEL}-${get(context).take(4)}"
}
