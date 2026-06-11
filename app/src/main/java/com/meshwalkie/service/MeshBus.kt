package com.meshwalkie.service

import com.meshwalkie.core.PeerRosterEntry
import com.meshwalkie.core.PeerView
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

    @Volatile var pttHandler: ((pressed: Boolean) -> Unit)? = null

    fun publishPeers(views: List<PeerView>) { _peers.value = views }
    fun publishRoster(entries: List<PeerRosterEntry>) { _roster.value = entries }
    fun publishHeading(deg: Float) { _myHeading.value = deg }
    fun publishWaitingForGps(waiting: Boolean) { _waitingForGps.value = waiting }
    fun publishLinkCount(n: Int) { _linkCount.value = n }
    fun publishStatus(text: String) { _status.value = text }
}
