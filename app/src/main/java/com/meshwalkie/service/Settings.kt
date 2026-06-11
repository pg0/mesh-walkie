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
    private const val KEY_QUICKTEXTS = "quick_texts"
    private val DEFAULT_QUICKTEXTS = listOf("OK", "Wait", "Help!", "On my way", "Turning back", "Regroup")
    private const val KEY_VAD = "vad_enabled"
    private const val KEY_VAD_SENS = "vad_sensitivity"
    private const val KEY_BREADCRUMB = "breadcrumb"

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

    /** Preset quick-texts shown on the radial wheel. */
    private val _quickTexts = MutableStateFlow(DEFAULT_QUICKTEXTS)
    val quickTexts: StateFlow<List<String>> = _quickTexts

    /** Voice-activated transmit (hands-free): auto-send a clip when speech is detected. */
    private val _vadEnabled = MutableStateFlow(false)
    val vadEnabled: StateFlow<Boolean> = _vadEnabled

    /** VAD sensitivity 0..100; higher = picks up quieter sound (lower energy threshold). */
    private val _vadSensitivity = MutableStateFlow(50)
    val vadSensitivity: StateFlow<Int> = _vadSensitivity

    /** Record a breadcrumb trail of my own fixes, for retrace / guide-me-back. */
    private val _breadcrumbEnabled = MutableStateFlow(false)
    val breadcrumbEnabled: StateFlow<Boolean> = _breadcrumbEnabled

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
        val saved = prefs.getString(KEY_QUICKTEXTS, null)
        _quickTexts.value = saved?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.ifEmpty { DEFAULT_QUICKTEXTS } ?: DEFAULT_QUICKTEXTS
        _vadEnabled.value = prefs.getBoolean(KEY_VAD, false)
        _vadSensitivity.value = prefs.getInt(KEY_VAD_SENS, 50)
        _breadcrumbEnabled.value = prefs.getBoolean(KEY_BREADCRUMB, false)
    }

    fun setBreadcrumbEnabled(on: Boolean) {
        _breadcrumbEnabled.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BREADCRUMB, on).apply()
    }

    fun setVadEnabled(on: Boolean) {
        _vadEnabled.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VAD, on).apply()
    }

    fun setVadSensitivity(value: Int) {
        val v = value.coerceIn(0, 100)
        _vadSensitivity.value = v
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_VAD_SENS, v).apply()
    }

    /** Save preset quick-texts (max 8, non-empty). */
    fun setQuickTexts(texts: List<String>) {
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }.take(8)
            .ifEmpty { DEFAULT_QUICKTEXTS }
        _quickTexts.value = clean
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUICKTEXTS, clean.joinToString("\n")).apply()
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
