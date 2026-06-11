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
    private const val KEY_BT_HEADSET = "bt_headset"
    private const val KEY_NET_HOST = "net_host"
    private const val KEY_NET_CLIENT = "net_client"
    private const val KEY_GPS = "gps_enabled"
    private const val KEY_OFFLINE_SOUND = "offline_sound"
    private const val KEY_VOLUME_PTT = "volume_ptt"
    private const val KEY_MUTE = "mute_sounds"
    private const val KEY_NIGHT = "night_mode"
    private const val KEY_TEXT_SOUND = "text_sound"
    private const val KEY_VOICE_BITRATE = "voice_bitrate"
    const val DEFAULT_VOICE_BITRATE = 23_850

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

    /** Route the mic capture to a connected Bluetooth headset (SCO). */
    private val _btHeadset = MutableStateFlow(false)
    val btHeadset: StateFlow<Boolean> = _btHeadset

    /** Internet fallback - host the in-app relay (this device becomes the server). */
    private val _internetHost = MutableStateFlow(false)
    val internetHost: StateFlow<Boolean> = _internetHost

    /** Internet fallback - join an announced host over the internet. */
    private val _internetClient = MutableStateFlow(false)
    val internetClient: StateFlow<Boolean> = _internetClient

    /** Share my GPS position on the mesh. Off = privacy, no position broadcast. */
    private val _gpsEnabled = MutableStateFlow(true)
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled

    /** Play an alert tone when a connected device drops off the mesh. */
    private val _offlineSound = MutableStateFlow(false)
    val offlineSound: StateFlow<Boolean> = _offlineSound

    /** Hold volume-down as push-to-talk (eyes-free, glove-friendly). */
    private val _volumePtt = MutableStateFlow(false)
    val volumePtt: StateFlow<Boolean> = _volumePtt

    /** Mute all alert tones (beeps). */
    private val _muteSounds = MutableStateFlow(false)
    val muteSounds: StateFlow<Boolean> = _muteSounds

    /** Night mode: red-tinted UI to preserve night vision. */
    private val _nightMode = MutableStateFlow(false)
    val nightMode: StateFlow<Boolean> = _nightMode

    /** Beep when a quick-text arrives. */
    private val _textSound = MutableStateFlow(true)
    val textSound: StateFlow<Boolean> = _textSound

    /** AMR-WB voice bitrate (bit/s). Higher = clearer, more data; lower = sparing. */
    private val _voiceBitrate = MutableStateFlow(DEFAULT_VOICE_BITRATE)
    val voiceBitrate: StateFlow<Int> = _voiceBitrate

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
        _btHeadset.value = prefs.getBoolean(KEY_BT_HEADSET, false)
        _internetHost.value = prefs.getBoolean(KEY_NET_HOST, false)
        _internetClient.value = prefs.getBoolean(KEY_NET_CLIENT, false)
        _gpsEnabled.value = prefs.getBoolean(KEY_GPS, true)
        _offlineSound.value = prefs.getBoolean(KEY_OFFLINE_SOUND, false)
        _volumePtt.value = prefs.getBoolean(KEY_VOLUME_PTT, false)
        _muteSounds.value = prefs.getBoolean(KEY_MUTE, false)
        _nightMode.value = prefs.getBoolean(KEY_NIGHT, false)
        _textSound.value = prefs.getBoolean(KEY_TEXT_SOUND, true)
        _voiceBitrate.value = prefs.getInt(KEY_VOICE_BITRATE, DEFAULT_VOICE_BITRATE)
    }

    fun setVoiceBitrate(rate: Int) {
        _voiceBitrate.value = rate
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_VOICE_BITRATE, rate).apply()
    }

    fun setTextSound(on: Boolean) {
        _textSound.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TEXT_SOUND, on).apply()
    }

    fun setVolumePtt(on: Boolean) {
        _volumePtt.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOLUME_PTT, on).apply()
    }

    fun setMuteSounds(on: Boolean) {
        _muteSounds.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MUTE, on).apply()
    }

    fun setNightMode(on: Boolean) {
        _nightMode.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NIGHT, on).apply()
    }

    fun setGpsEnabled(on: Boolean) {
        _gpsEnabled.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GPS, on).apply()
    }

    fun setOfflineSound(on: Boolean) {
        _offlineSound.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OFFLINE_SOUND, on).apply()
    }

    fun setBtHeadset(on: Boolean) {
        _btHeadset.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BT_HEADSET, on).apply()
    }

    fun setInternetHost(on: Boolean) {
        _internetHost.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NET_HOST, on).apply()
    }

    fun setInternetClient(on: Boolean) {
        _internetClient.value = on
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NET_CLIENT, on).apply()
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
