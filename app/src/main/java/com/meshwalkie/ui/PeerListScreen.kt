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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.core.Display
import com.meshwalkie.core.Freshness
import com.meshwalkie.core.PeerView
import com.meshwalkie.service.MeshBus

@Composable
fun PeerListScreen() {
    val peers by MeshBus.peers.collectAsStateWithLifecycle()
    val heading by MeshBus.myHeading.collectAsStateWithLifecycle()
    val waitingForGps by MeshBus.waitingForGps.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mesh Walkie", style = MaterialTheme.typography.headlineSmall)
        if (waitingForGps) {
            Text("Warte auf GPS-Fix", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(peers, key = { it.id }) { peer ->
                PeerRow(peer = peer, myHeadingDeg = heading)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
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
            Text(peer.name, style = MaterialTheme.typography.bodyMedium)
        }
        FreshnessDot(peer.freshness)
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
