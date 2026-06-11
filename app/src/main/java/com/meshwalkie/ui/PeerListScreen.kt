package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.core.Display
import com.meshwalkie.core.Freshness
import com.meshwalkie.core.GeoMath
import com.meshwalkie.core.PeerRosterEntry
import com.meshwalkie.core.PeerView
import com.meshwalkie.core.WaypointView
import com.meshwalkie.service.MeshBus
import com.meshwalkie.service.Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeerListScreen(onOpenSettings: () -> Unit, onExit: () -> Unit) {
    val peers by MeshBus.peers.collectAsStateWithLifecycle()
    val heading by MeshBus.myHeading.collectAsStateWithLifecycle()
    val waitingForGps by MeshBus.waitingForGps.collectAsStateWithLifecycle()

    val status by MeshBus.status.collectAsStateWithLifecycle()
    val linkCount by MeshBus.linkCount.collectAsStateWithLifecycle()
    val roster by MeshBus.roster.collectAsStateWithLifecycle()
    val lastVoice by MeshBus.lastVoice.collectAsStateWithLifecycle()
    val sentStatus by MeshBus.sentStatus.collectAsStateWithLifecycle()
    val messages by MeshBus.messages.collectAsStateWithLifecycle()
    val myLoc by MeshBus.myLocation.collectAsStateWithLifecycle()
    val waypoints by MeshBus.waypoints.collectAsStateWithLifecycle()
    val target by MeshBus.target.collectAsStateWithLifecycle()
    val vadOn by Settings.vadEnabled.collectAsStateWithLifecycle()
    val hosts by MeshBus.hosts.collectAsStateWithLifecycle()
    val myHostIp by MeshBus.myHostIp.collectAsStateWithLifecycle()
    val joinedServer by MeshBus.joinedServer.collectAsStateWithLifecycle()
    val hostClients by MeshBus.hostClientCount.collectAsStateWithLifecycle()
    val hostIds = hosts.map { it.id }.toSet()
    var viewMode by remember { mutableIntStateOf(0) }   // 0 list, 1 radar, 2 map
    var showType by remember { mutableStateOf(false) }
    var showWp by remember { mutableStateOf(false) }
    var showDropTarget by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }
    // Ticks every few seconds so relative ages ("12s ago") keep updating.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(5000); nowTick = System.currentTimeMillis() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mesh Walkie", style = MaterialTheme.typography.headlineSmall)
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Servers") },
                        onClick = { menuOpen = false; showServers = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menuOpen = false; onOpenSettings() }
                    )
                    DropdownMenuItem(
                        text = { Text("Exit") },
                        onClick = { menuOpen = false; onExit() }
                    )
                }
            }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        when {
            myHostIp != null -> Text("🌐 Hosting - $hostClients client(s)", style = MaterialTheme.typography.bodySmall)
            joinedServer -> Text("🌐 Joined online host", style = MaterialTheme.typography.bodySmall)
        }
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
            TextButton(onClick = { viewMode = 0 }) { Text(if (viewMode == 0) "List ●" else "List") }
            TextButton(onClick = { viewMode = 1 }) { Text(if (viewMode == 1) "Radar ●" else "Radar") }
            TextButton(onClick = { viewMode = 2 }) { Text(if (viewMode == 2) "Map ●" else "Map") }
        }

        when (viewMode) {
            1 -> RadarView(peers, waypoints, heading, Modifier.weight(1f).fillMaxWidth().clipToBounds())
            2 -> MapScreen(
                peers, myLoc, waypoints, target,
                onSetTarget = { la, lo -> MeshBus.setTarget(la, lo) },
                Modifier.weight(1f).fillMaxWidth().clipToBounds()
            )
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
            // Pinned navigation target on top.
            val t = target
            val ml = myLoc
            if (t != null && ml != null) {
                item {
                    TargetRow(
                        t, ml, heading,
                        onDrop = { showDropTarget = true },
                        onClear = { MeshBus.clearTarget() }
                    )
                }
            }
            // Users next (full arrow rows when positions are known).
            items(peers, key = { "p_${it.id}" }) { peer ->
                PeerRow(peer = peer, myHeadingDeg = heading, isHost = peer.id in hostIds)
            }
            // Then shared waypoints (rally points).
            items(waypoints, key = { "w_${it.id}" }) { wp ->
                WaypointRow(wp, heading) { MeshBus.removeWaypointHandler?.invoke(wp.id) }
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
                    RosterRow(entry, isHost = entry.id in hostIds)
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
        }
        if (messages.isNotEmpty()) {
            // reverseLayout = newest pinned at the bottom and visible
            LazyColumn(
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 56.dp)   // ~2 lines, so it never shrinks the map
                    .padding(top = 8.dp)
            ) {
                items(messages.asReversed()) { (text, atMs) ->
                    Text(
                        "💬 $text  ·  ${Display.formatAge(nowTick - atMs)} ago",
                        style = MaterialTheme.typography.bodyMedium
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
                Text("🔊 $lv", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { MeshBus.replayHandler?.invoke() }) { Text("Replay") }
            }
        }
        sentStatus?.let { ss ->
            Text("✅ $ss", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            QuickTextWheel(onSend = { MeshBus.sendTextHandler?.invoke(it) })
            TextButton(onClick = { showType = true }) { Text("Msg") }
            TextButton(onClick = { showWp = true }) { Text("📍Waypoint") }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!vadOn) {
                PttButton(onPtt = { pressed -> MeshBus.pttHandler?.invoke(pressed) })
            } else {
                Text("Auto-talk active", style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Auto-talk", style = MaterialTheme.typography.labelMedium)
                Switch(checked = vadOn, onCheckedChange = { Settings.setVadEnabled(it) })
            }
        }

        if (showType) {
            TextInputDialog(
                title = "Send message",
                confirmLabel = "Send",
                onConfirm = { MeshBus.sendTextHandler?.invoke(it) },
                onDismiss = { showType = false }
            )
        }
        if (showWp) {
            TextInputDialog(
                title = "Drop waypoint here",
                confirmLabel = "Drop",
                onConfirm = { MeshBus.dropWaypointHandler?.invoke(it) },
                onDismiss = { showWp = false },
                allowEmpty = true   // empty -> service uses an ISO datetime label
            )
        }
        if (showServers) {
            ServerDialog(onDismiss = { showServers = false })
        }
        if (showDropTarget) {
            val t = target
            TextInputDialog(
                title = "Drop target as waypoint",
                confirmLabel = "Drop",
                onConfirm = { label -> t?.let { MeshBus.dropWaypointAtHandler?.invoke(it.first, it.second, label) } },
                onDismiss = { showDropTarget = false }
            )
        }
    }
}

@Composable
fun PeerRow(peer: PeerView, myHeadingDeg: Float, isHost: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        // arrow rotation = bearingToPeer - myHeading (tested in Task 3);
        // a peer within ~5 m has no meaningful direction -> show a ball
        ArrowIcon(
            rotationDeg = Display.arrowRotation(peer.bearingDeg, myHeadingDeg.toDouble()),
            ball = peer.distanceMeters < 5.0
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // e.g. "600 m NNW"
                text = "${Display.formatDistance(peer.distanceMeters)} ${Display.compassLabel(peer.bearingDeg)}",
                style = MaterialTheme.typography.titleMedium
            )
            val nameLine = if (peer.freshness == Freshness.STALE) "${peer.name} - offline, seen ${Display.formatAge(peer.ageMs)} ago" else peer.name
            val batt = if (peer.batteryPct in 0..100) "  🔋${peer.batteryPct}%" else ""
            val host = if (isHost) "  🌐" else ""
            Text(nameLine + batt + host, style = MaterialTheme.typography.bodyMedium)
        }
        FreshnessDot(peer.freshness)
    }
}

@Composable
fun TargetRow(
    target: Pair<Double, Double>,
    myLoc: Pair<Double, Double>,
    myHeadingDeg: Float,
    onDrop: () -> Unit,
    onClear: () -> Unit
) {
    val dist = GeoMath.distanceMeters(myLoc.first, myLoc.second, target.first, target.second)
    val bearing = GeoMath.bearingDegrees(myLoc.first, myLoc.second, target.first, target.second)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        ArrowIcon(
            rotationDeg = Display.arrowRotation(bearing, myHeadingDeg.toDouble()),
            ball = dist < 5.0,
            color = Color(0xFF2E7D32)   // green = navigation target
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "🎯 TARGET  ${Display.formatDistance(dist)} ${Display.compassLabel(bearing)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text("tap the map to move it", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDrop) { Text("Drop") }
        TextButton(onClick = onClear) { Text("✕") }
    }
}

@Composable
fun WaypointRow(wp: WaypointView, myHeadingDeg: Float, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        ArrowIcon(
            rotationDeg = Display.arrowRotation(wp.bearingDeg, myHeadingDeg.toDouble()),
            ball = wp.distanceMeters < 5.0,
            color = Color(0xFFFFB300)   // amber = waypoint
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "📍 ${wp.label}  ${Display.formatDistance(wp.distanceMeters)} ${Display.compassLabel(wp.bearingDeg)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text("by ${wp.senderName}", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onDelete) { Text("✕") }
    }
}

@Composable
fun RosterRow(entry: PeerRosterEntry, isHost: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name + if (isHost) "  🌐" else "", style = MaterialTheme.typography.titleMedium)
            val proximity = if (entry.hops == 0) "direct (near)" else "${entry.hops} hops (~${entry.hops * 75} m)"
            val offline = if (entry.freshness == Freshness.STALE) "offline ${Display.formatAge(entry.ageMs)} ago - " else ""
            val batt = if (entry.batteryPct in 0..100) " - 🔋${entry.batteryPct}%" else ""
            Text(
                "$offline$proximity - ID ${entry.id}$batt",
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
