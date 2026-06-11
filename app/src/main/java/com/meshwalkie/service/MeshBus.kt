package com.meshwalkie.service

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

    private val _myHeading = MutableStateFlow(0f)
    val myHeading: StateFlow<Float> = _myHeading

    /** True until the LOCAL device gets its first GPS fix; UI can show "Warte auf GPS-Fix". */
    private val _waitingForGps = MutableStateFlow(true)
    val waitingForGps: StateFlow<Boolean> = _waitingForGps

    @Volatile var pttHandler: ((pressed: Boolean) -> Unit)? = null

    fun publishPeers(views: List<PeerView>) { _peers.value = views }
    fun publishHeading(deg: Float) { _myHeading.value = deg }
    fun publishWaitingForGps(waiting: Boolean) { _waitingForGps.value = waiting }
}
