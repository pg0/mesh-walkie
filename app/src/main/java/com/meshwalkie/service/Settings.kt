package com.meshwalkie.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User-editable, persisted settings: display name + dark mode. Backed by the
 * same SharedPreferences file as [DeviceId]. Call [init] once (from the
 * Activity and the Service) before reading the flows.
 */
object Settings {
    private const val PREFS = "meshwalkie"
    private const val KEY_NAME = "display_name"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_GROUP = "group_code"
    const val DEFAULT_GROUP = "channel-1"

    private lateinit var appContext: Context

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName

    // Default ON: this is an OLED-friendly app, dark by default.
    private val _darkMode = MutableStateFlow(true)
    val darkMode: StateFlow<Boolean> = _darkMode

    /**
     * Mesh group code. Phones connect only to others with the same code
     * (serviceId is derived from it). This is the group boundary + access
     * control: share a private code, only code-holders join.
     */
    private val _groupCode = MutableStateFlow(DEFAULT_GROUP)
    val groupCode: StateFlow<String> = _groupCode

    /** Stable device id (read-only), shown in settings. */
    var deviceId: String = ""
        private set

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        deviceId = DeviceId.get(appContext)
        _displayName.value = prefs.getString(KEY_NAME, null) ?: DeviceId.displayName(appContext)
        _darkMode.value = prefs.getBoolean(KEY_DARK, true)
        _groupCode.value = prefs.getString(KEY_GROUP, DEFAULT_GROUP) ?: DEFAULT_GROUP
    }

    /**
     * Set the group code. Returns true if it actually changed (caller should
     * then rejoin the mesh). Sanitised to serviceId-safe chars.
     */
    fun setGroupCode(code: String): Boolean {
        val clean = code.trim().lowercase()
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(24)
            .ifBlank { DEFAULT_GROUP }
        if (clean == _groupCode.value) return false
        _groupCode.value = clean
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROUP, clean).apply()
        return true
    }

    fun setDisplayName(name: String) {
        val clean = name.trim().take(32).ifBlank { DeviceId.displayName(appContext) }
        _displayName.value = clean
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NAME, clean).apply()
    }

    fun setDarkMode(on: Boolean) {
        _darkMode.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, on).apply()
    }
}
