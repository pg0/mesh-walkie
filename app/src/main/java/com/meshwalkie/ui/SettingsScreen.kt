package com.meshwalkie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.MeshBus
import com.meshwalkie.service.Settings

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // System back returns to the main screen instead of closing the app.
    BackHandler { onBack() }

    val dark by Settings.darkMode.collectAsStateWithLifecycle()
    val vadOn by Settings.vadEnabled.collectAsStateWithLifecycle()
    val vadSens by Settings.vadSensitivity.collectAsStateWithLifecycle()
    val btHeadsetOn by Settings.btHeadset.collectAsStateWithLifecycle()
    val netHost by Settings.internetHost.collectAsStateWithLifecycle()
    val netClient by Settings.internetClient.collectAsStateWithLifecycle()
    val gpsOn by Settings.gpsEnabled.collectAsStateWithLifecycle()
    val myHostIp by MeshBus.myHostIp.collectAsStateWithLifecycle()
    val savedName by Settings.displayName.collectAsStateWithLifecycle()
    val savedGroup by Settings.groupCode.collectAsStateWithLifecycle()
    val savedQuickTexts by Settings.quickTexts.collectAsStateWithLifecycle()

    var nameField by remember { mutableStateOf(savedName) }
    var groupField by remember { mutableStateOf(savedGroup) }
    var quickTextsField by remember { mutableStateOf(savedQuickTexts.joinToString("\n")) }

    val save = {
        Settings.setDisplayName(nameField)
        Settings.setQuickTexts(quickTextsField.split("\n"))
        // setGroupCode returns true when it changed; the service observes
        // the flow and rejoins the new mesh automatically.
        Settings.setGroupCode(groupField)
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = onBack) { Text("Back") }
                Button(onClick = save) { Text("Save") }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Name", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = nameField,
            onValueChange = { nameField = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Text("Channel", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = groupField,
            onValueChange = { groupField = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Everyone on the same channel hears each other. Pick a shared name (e.g. team-alpha) for a private channel.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(
            onClick = { groupField = Settings.DEFAULT_GROUP },
            modifier = Modifier.padding(top = 2.dp)
        ) { Text("Use default channel (${Settings.DEFAULT_GROUP})") }

        Spacer(Modifier.height(20.dp))

        Text("Quick texts (one per line, max 8)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = quickTextsField,
            onValueChange = { quickTextsField = it },
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dark mode (OLED)", style = MaterialTheme.typography.labelLarge)
            Switch(checked = dark, onCheckedChange = { Settings.setDarkMode(it) })
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Share my GPS position", style = MaterialTheme.typography.labelLarge)
            Switch(checked = gpsOn, onCheckedChange = { Settings.setGpsEnabled(it) })
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto-talk (VAD, hands-free)", style = MaterialTheme.typography.labelLarge)
            Switch(checked = vadOn, onCheckedChange = { Settings.setVadEnabled(it) })
        }
        if (vadOn) {
            Text("Sensitivity: $vadSens (higher = picks up quieter)", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = vadSens.toFloat(),
                onValueChange = { Settings.setVadSensitivity(it.toInt()) },
                valueRange = 0f..100f
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bluetooth headset mic", style = MaterialTheme.typography.labelLarge)
            Switch(checked = btHeadsetOn, onCheckedChange = { Settings.setBtHeadset(it) })
        }

        Spacer(Modifier.height(20.dp))

        Text("Fallback via internet", style = MaterialTheme.typography.labelLarge)
        Text(
            "Extends range beyond the BLE mesh. Host here; others join from the Server menu on the main screen (your IPv6 is shared on the mesh, or enter it manually).",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Host (this device is the server)", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = netHost, onCheckedChange = { Settings.setInternetHost(it) })
        }
        myHostIp?.let { hip ->
            val clipboard = LocalClipboardManager.current
            val ctx = LocalContext.current
            val addr = "[$hip]:51820"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hosting at $addr", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(addr)) }) { Text("Copy") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my Mesh Walkie host: $addr")
                    }
                    ctx.startActivity(
                        Intent.createChooser(send, "Share host address")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Share") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Client (auto-join a host)", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = netClient, onCheckedChange = { Settings.setInternetClient(it) })
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "Device ID: ${Settings.deviceId}",
            style = MaterialTheme.typography.bodySmall
        )
        val ctx = LocalContext.current
        val versionLine = remember {
            @Suppress("DEPRECATION")
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val installed = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date(pi.lastUpdateTime))
            "v${pi.versionName} - installed $installed"
        }
        Text(versionLine, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}
