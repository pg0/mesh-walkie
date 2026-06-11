package com.meshwalkie.service

import com.meshwalkie.core.PeerRosterEntry
import com.meshwalkie.core.PeerView
import com.meshwalkie.core.WaypointView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An announced internet relay host. */
data class HostInfo(val id: String, val name: String, val ip: String, val port: Int)

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

    /** Shared countdown: (label, endAtMs in local time). Null = none active. */
    private val _countdown = MutableStateFlow<Pair<String, Long>?>(null)
    val countdown: StateFlow<Pair<String, Long>?> = _countdown
    fun publishCountdown(value: Pair<String, Long>?) { _countdown.value = value }
    @Volatile var startCountdownHandler: ((label: String, seconds: Int) -> Unit)? = null

    /** My current GPS speed in m/s, for ETA to a target. */
    private val _mySpeed = MutableStateFlow(0.0)
    val mySpeed: StateFlow<Double> = _mySpeed
    fun publishMySpeed(mps: Double) { _mySpeed.value = mps }

    fun setTarget(lat: Double, lon: Double) { _target.value = lat to lon }
    fun clearTarget() { _target.value = null }

    /** Announced internet relay hosts (from the mesh), newest info per id. */
    private val _hosts = MutableStateFlow<List<HostInfo>>(emptyList())
    val hosts: StateFlow<List<HostInfo>> = _hosts

    fun addHost(info: HostInfo) {
        _hosts.value = (_hosts.value.filter { it.id != info.id } + info).takeLast(8)
    }

    /** My own public IPv6 while hosting; null when not hosting. */
    private val _myHostIp = MutableStateFlow<String?>(null)
    val myHostIp: StateFlow<String?> = _myHostIp
    fun publishMyHostIp(ip: String?) { _myHostIp.value = ip }

    /** True while connected to a host as a client (so only then can you Leave). */
    private val _joinedServer = MutableStateFlow(false)
    val joinedServer: StateFlow<Boolean> = _joinedServer
    fun publishJoinedServer(on: Boolean) { _joinedServer.value = on }

    /** Number of clients connected to me while I host. */
    private val _hostClientCount = MutableStateFlow(0)
    val hostClientCount: StateFlow<Int> = _hostClientCount
    fun publishHostClientCount(n: Int) { _hostClientCount.value = n }

    /** Set by the service; UI calls it to join a host at ip:port. */
    @Volatile var joinHandler: ((ip: String, port: Int) -> Unit)? = null
    /** Set by the service; UI calls it to leave the joined host. */
    @Volatile var leaveHostHandler: (() -> Unit)? = null


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

    /** Delivery status of my last sent clip, e.g. "Heard by 2". Null = none. */
    private val _sentStatus = MutableStateFlow<String?>(null)
    val sentStatus: StateFlow<String?> = _sentStatus

    /** Quick-text history (text, receivedAtMs), newest last, capped at 10. */
    private val _messages = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val messages: StateFlow<List<Pair<String, Long>>> = _messages

    @Volatile var pttHandler: ((pressed: Boolean) -> Unit)? = null

    /** Set by the service; UI toggles continuous live broadcast (babyfon / live mode). */
    @Volatile var liveBroadcastHandler: ((on: Boolean) -> Unit)? = null

    /** True while THIS device is live-broadcasting; drives the Live button state. */
    private val _liveBroadcasting = MutableStateFlow(false)
    val liveBroadcasting: StateFlow<Boolean> = _liveBroadcasting
    fun publishLiveBroadcasting(on: Boolean) { _liveBroadcasting.value = on }

    /**
     * True while the mic is actually capturing a PTT clip. Goes false the moment
     * the recorder stops - including the auto-cut at the max-duration cap while
     * the finger is still down, so the UI can flag "already sent, release".
     */
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording
    fun publishRecording(on: Boolean) { _recording.value = on }

    /** Set by the service; UI calls it to drop a waypoint at my position. */
    @Volatile var dropWaypointHandler: ((label: String) -> Unit)? = null

    /** Set by the service; UI calls it to delete a waypoint locally by id. */
    @Volatile var removeWaypointHandler: ((id: String) -> Unit)? = null

    /** Set by the service; UI calls it to drop a waypoint at given coords (e.g. the target). */
    @Volatile var dropWaypointAtHandler: ((lat: Double, lon: Double, label: String) -> Unit)? = null

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
    fun publishSentStatus(text: String) { _sentStatus.value = text }
    fun publishText(text: String) {
        _messages.value = (_messages.value + (text to System.currentTimeMillis())).takeLast(10)
    }
}
