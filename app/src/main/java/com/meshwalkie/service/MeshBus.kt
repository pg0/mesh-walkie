package com.meshwalkie.service

import com.meshwalkie.core.PeerRosterEntry
import com.meshwalkie.core.PeerView
import com.meshwalkie.core.WaypointView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bridge: MeshService writes, Compose reads.
 * pttHandler is set by the service; UI calls it with pressed=true/false.
 */
object MeshBus {
    private val _peers = MutableStateFlow<List<PeerView>>(emptyList())
    val peers: StateFlow<List<PeerView>> = _peers

    /** All known peers regardless of GPS, so the UI shows who is connected. */
    private val _roster = MutableStateFlow<List<PeerRosterEntry>>(emptyList())
    val roster: StateFlow<List<PeerRosterEntry>> = _roster

    /** My own (lat, lon) once fixed, for the map view. Null until first fix. */
    private val _myLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val myLocation: StateFlow<Pair<Double, Double>?> = _myLocation

    /** Dropped waypoints with distance/bearing from me. */
    private val _waypoints = MutableStateFlow<List<WaypointView>>(emptyList())
    val waypoints: StateFlow<List<WaypointView>> = _waypoints

    /** A personal navigation target (lat, lon) set by tapping the map. Null = none. */
    private val _target = MutableStateFlow<Pair<Double, Double>?>(null)
    val target: StateFlow<Pair<Double, Double>?> = _target

    fun setTarget(lat: Double, lon: Double) { _target.value = lat to lon }
    fun clearTarget() { _target.value = null }

    private val _myHeading = MutableStateFlow(0f)
    val myHeading: StateFlow<Float> = _myHeading

    /** True until the LOCAL device gets its first GPS fix; UI can show "Warte auf GPS-Fix". */
    private val _waitingForGps = MutableStateFlow(true)
    val waitingForGps: StateFlow<Boolean> = _waitingForGps

    /** Number of currently connected mesh links (Nearby endpoints). */
    private val _linkCount = MutableStateFlow(0)
    val linkCount: StateFlow<Int> = _linkCount

    /** Human-readable mesh status, e.g. "Suche Geraete…" / "1 Geraet verbunden". */
    private val _status = MutableStateFlow("Starting mesh…")
    val status: StateFlow<String> = _status

    /** Label for the last received voice message, e.g. "Last from Alice". Null = none yet. */
    private val _lastVoice = MutableStateFlow<String?>(null)
    val lastVoice: StateFlow<String?> = _lastVoice

    /** Quick-text history (sent + received), newest last, capped at 10. */
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    @Volatile var pttHandler: ((pressed: Boolean) -> Unit)? = null

    /** Set by the service; UI calls it to drop a waypoint at my position. */
    @Volatile var dropWaypointHandler: ((label: String) -> Unit)? = null

    /** Set by the service; UI calls it to replay the last received clip. */
    @Volatile var replayHandler: (() -> Unit)? = null

    /** Set by the service; UI calls it to send a quick-text. */
    @Volatile var sendTextHandler: ((String) -> Unit)? = null

    fun publishPeers(views: List<PeerView>) { _peers.value = views }
    fun publishRoster(entries: List<PeerRosterEntry>) { _roster.value = entries }
    fun publishMyLocation(lat: Double, lon: Double) { _myLocation.value = lat to lon }
    fun publishWaypoints(list: List<WaypointView>) { _waypoints.value = list }
    fun publishHeading(deg: Float) { _myHeading.value = deg }
    fun publishWaitingForGps(waiting: Boolean) { _waitingForGps.value = waiting }
    fun publishLinkCount(n: Int) { _linkCount.value = n }
    fun publishStatus(text: String) { _status.value = text }
    fun publishLastVoice(text: String) { _lastVoice.value = text }
    fun publishText(text: String) { _messages.value = (_messages.value + text).takeLast(10) }
}
