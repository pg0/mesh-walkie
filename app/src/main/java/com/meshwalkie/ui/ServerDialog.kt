package com.meshwalkie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.MeshBus

/**
 * Internet relay servers: pick an announced host (discovered over the mesh) to
 * join, or connect to one manually by IP. Leave disconnects.
 */
@Composable
fun ServerDialog(onDismiss: () -> Unit) {
    val hosts by MeshBus.hosts.collectAsStateWithLifecycle()
    val joined by MeshBus.joinedServer.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("51820") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Internet servers") },
        text = {
            Column {
                if (hosts.isEmpty()) {
                    Text("No hosts announced on the mesh yet.")
                } else {
                    hosts.forEach { h ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${h.name}  [${h.ip}]",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { clipboard.setText(AnnotatedString(h.ip)) }
                            )
                            TextButton(onClick = {
                                MeshBus.joinHandler?.invoke(h.ip, h.port); onDismiss()
                            }) { Text("Join") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Connect by IP")
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it },
                    label = { Text("host IPv6 / IP") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it },
                    label = { Text("port") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (ip.isNotBlank()) MeshBus.joinHandler?.invoke(ip.trim(), port.toIntOrNull() ?: 51820)
                onDismiss()
            }) { Text("Connect") }
        },
        dismissButton = {
            Row {
                if (joined) {
                    TextButton(onClick = { MeshBus.leaveHostHandler?.invoke(); onDismiss() }) { Text("Leave") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
