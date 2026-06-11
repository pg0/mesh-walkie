package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.core.Display
import com.meshwalkie.core.Freshness
import com.meshwalkie.core.PeerRosterEntry
import com.meshwalkie.core.PeerView
import com.meshwalkie.service.MeshBus

@Composable
fun PeerListScreen(onOpenSettings: () -> Unit) {
    val peers by MeshBus.peers.collectAsStateWithLifecycle()
    val heading by MeshBus.myHeading.collectAsStateWithLifecycle()
    val waitingForGps by MeshBus.waitingForGps.collectAsStateWithLifecycle()

    val status by MeshBus.status.collectAsStateWithLifecycle()
    val linkCount by MeshBus.linkCount.collectAsStateWithLifecycle()
    val roster by MeshBus.roster.collectAsStateWithLifecycle()
    val lastVoice by MeshBus.lastVoice.collectAsStateWithLifecycle()
    var showRadar by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mesh Walkie", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (waitingForGps) {
            Text(
                "Waiting for GPS fix - arrow and distance appear once both phones have a fix outdoors.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = { showRadar = false }) { Text(if (showRadar) "List" else "List ●") }
            TextButton(onClick = { showRadar = true }) { Text(if (showRadar) "Radar ●" else "Radar") }
        }

        if (showRadar) {
            RadarView(peers, heading, Modifier.weight(1f).fillMaxWidth())
        } else LazyColumn(modifier = Modifier.weight(1f)) {
            // Full arrow rows when positions are known.
            items(peers, key = { "p_${it.id}" }) { peer ->
                PeerRow(peer = peer, myHeadingDeg = heading)
            }
            // Connected peers without a usable position yet (no GPS on a side):
            // show name + id + freshness so you can see who is on the mesh.
            val positioned = peers.map { it.id }.toSet()
            val rosterOnly = roster.filter { it.id !in positioned }
            if (rosterOnly.isNotEmpty()) {
                item {
                    Text(
                        "Connected (waiting for GPS)",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(rosterOnly, key = { "r_${it.id}" }) { entry ->
                    RosterRow(entry)
                }
            }
            if (peers.isEmpty() && roster.isEmpty()) {
                item {
                    Text(
                        if (linkCount == 0) "No other device in range. Open the app on a second phone with the same group."
                        else "Connected, waiting for peer data…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        lastVoice?.let { lv ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(lv, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { MeshBus.replayHandler?.invoke() }) { Text("Replay") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            PttButton(onPtt = { pressed -> MeshBus.pttHandler?.invoke(pressed) })
        }
    }
}

@Composable
fun PeerRow(peer: PeerView, myHeadingDeg: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        // arrow rotation = bearingToPeer - myHeading (tested in Task 3)
        ArrowIcon(rotationDeg = Display.arrowRotation(peer.bearingDeg, myHeadingDeg.toDouble()))
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // e.g. "600 m NNW"
                text = "${Display.formatDistance(peer.distanceMeters)} ${Display.compassLabel(peer.bearingDeg)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (peer.freshness == Freshness.STALE) "${peer.name} - offline (last position)" else peer.name,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        FreshnessDot(peer.freshness)
    }
}

@Composable
fun RosterRow(entry: PeerRosterEntry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium)
            val proximity = if (entry.hops == 0) "direct (near)" else "${entry.hops} hops (~${entry.hops * 75} m)"
            val offline = if (entry.freshness == Freshness.STALE) "offline - " else ""
            Text(
                "$offline$proximity - ID ${entry.id}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        FreshnessDot(entry.freshness)
    }
}

@Composable
fun FreshnessDot(freshness: Freshness) {
    val color = when (freshness) {
        Freshness.FRESH -> Color(0xFF2E7D32)  // green <30 s
        Freshness.AGING -> Color(0xFFF9A825)  // yellow <2 min
        Freshness.STALE -> Color(0xFFC62828)  // red - stale position is a lie
    }
    Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
}
