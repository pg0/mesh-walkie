package com.meshwalkie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.core.AppTheme
import com.meshwalkie.service.MeshBus
import com.meshwalkie.service.Settings

@Composable
fun SettingsScreen(onBack: () -> Unit) {

    val theme by Settings.theme.collectAsStateWithLifecycle()
    val liveVoiceOnly by Settings.liveVoiceOnly.collectAsStateWithLifecycle()
    val btHeadsetOn by Settings.btHeadset.collectAsStateWithLifecycle()
    val netHost by Settings.internetHost.collectAsStateWithLifecycle()
    val netClient by Settings.internetClient.collectAsStateWithLifecycle()
    val gpsOn by Settings.gpsEnabled.collectAsStateWithLifecycle()
    val offlineSound by Settings.offlineSound.collectAsStateWithLifecycle()
    val volumePtt by Settings.volumePtt.collectAsStateWithLifecycle()
    val muteSounds by Settings.muteSounds.collectAsStateWithLifecycle()
    val textSound by Settings.textSound.collectAsStateWithLifecycle()
    val voiceBitrate by Settings.voiceBitrate.collectAsStateWithLifecycle()
    val earpieceProx by Settings.earpieceProximity.collectAsStateWithLifecycle()
    val myHostIp by MeshBus.myHostIp.collectAsStateWithLifecycle()
    val savedName by Settings.displayName.collectAsStateWithLifecycle()
    val savedGroup by Settings.groupCode.collectAsStateWithLifecycle()
    val savedQuickTexts by Settings.quickTexts.collectAsStateWithLifecycle()

    var nameField by remember { mutableStateOf(savedName) }
    var groupField by remember { mutableStateOf(savedGroup) }
    var quickTextsField by remember { mutableStateOf(savedQuickTexts.joinToString("\n")) }

    // Persist all typed fields. Text fields auto-save on focus loss; this also
    // runs on Back in case a field is still focused when the screen leaves.
    // setGroupCode returns true only when changed; the service then rejoins.
    val persistAll = {
        Settings.setDisplayName(nameField)
        Settings.setQuickTexts(quickTextsField.split("\n"))
        Settings.setGroupCode(groupField)
    }
    val leave = { persistAll(); onBack() }

    // System back returns to the main screen (saving first) instead of closing.
    BackHandler { leave() }

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
            AppButton(onClick = leave) { Text("Back") }
        }

        Spacer(Modifier.height(24.dp))

        var nameWasFocused by remember { mutableStateOf(false) }
        Text("Name", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = nameField,
            onValueChange = { nameField = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { st ->
                    if (st.isFocused) nameWasFocused = true
                    else if (nameWasFocused) { Settings.setDisplayName(nameField); nameWasFocused = false }
                }
        )

        Spacer(Modifier.height(20.dp))

        var groupWasFocused by remember { mutableStateOf(false) }
        Text("Channel", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = groupField,
            onValueChange = { groupField = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { st ->
                    if (st.isFocused) groupWasFocused = true
                    else if (groupWasFocused) { Settings.setGroupCode(groupField); groupWasFocused = false }
                }
        )
        Text(
            "Everyone on the same channel hears each other. Pick a shared name (e.g. team-alpha) for a private channel.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        AppTextButton(
            onClick = { groupField = Settings.DEFAULT_GROUP },
            modifier = Modifier.padding(top = 2.dp)
        ) { Text("Use default channel (${Settings.DEFAULT_GROUP})") }

        Spacer(Modifier.height(20.dp))

        var quickWasFocused by remember { mutableStateOf(false) }
        Text("Quick texts (one per line, max 8)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = quickTextsField,
            onValueChange = { quickTextsField = it },
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { st ->
                    if (st.isFocused) quickWasFocused = true
                    else if (quickWasFocused) { Settings.setQuickTexts(quickTextsField.split("\n")); quickWasFocused = false }
                }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("Theme")
        listOf(
            AppTheme.FIELD to "Field (paper)",
            AppTheme.CORRUPTION to "Corruption (brutalist)",
            AppTheme.RADIO to "Radio (white/red)",
            AppTheme.DARK to "Dark (OLED)",
            AppTheme.NIGHT to "Night (red, night vision)"
        ).forEach { (t, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Settings.setTheme(t) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = theme == t, onClick = { Settings.setTheme(t) })
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Share my GPS position", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = gpsOn, onCheckedChange = { Settings.setGpsEnabled(it) })
        }

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sound if a device goes offline", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = offlineSound, onCheckedChange = { Settings.setOfflineSound(it) })
        }

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Volume-down = push to talk", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = volumePtt, onCheckedChange = { Settings.setVolumePtt(it) })
        }

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mute all sounds", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = muteSounds, onCheckedChange = { Settings.setMuteSounds(it) })
        }

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Text message sound", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = textSound, onCheckedChange = { Settings.setTextSound(it) })
        }

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Live: only stream when speaking", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = liveVoiceOnly, onCheckedChange = { Settings.setLiveVoiceOnly(it) })
        }
        Text(
            "Live mode drops silent/room-noise chunks and only sends your voice. Off = continuous stream (e.g. baby monitor).",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bluetooth headset mic", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = btHeadsetOn, onCheckedChange = { Settings.setBtHeadset(it) })
        }

        Spacer(Modifier.height(8.dp))
        AppDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hold to ear -> earpiece (loud places)", style = MaterialTheme.typography.labelLarge)
            AppSwitch(checked = earpieceProx, onCheckedChange = { Settings.setEarpieceProximity(it) })
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader("Voice quality (AMR-WB)")
        Text(
            "Higher = clearer, more data per clip. Lower stretches weak/long links.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Sparing\n12.65k" to 12_650, "Medium\n15.85k" to 15_850, "Best\n23.85k" to 23_850)
                .forEach { (label, rate) ->
                    if (voiceBitrate == rate) {
                        AppButton(onClick = { Settings.setVoiceBitrate(rate) }, modifier = Modifier.weight(1f)) { Text(label) }
                    } else {
                        AppOutlinedButton(onClick = { Settings.setVoiceBitrate(rate) }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader("Fallback via WiFi")
        Text(
            "Extends range beyond the BLE mesh over a shared WiFi/hotspot. Host here; others join from the Server menu on the main screen (your IP is shared on the mesh, or enter it manually).",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Host (this device is the server)", style = MaterialTheme.typography.bodyMedium)
            AppSwitch(checked = netHost, onCheckedChange = { Settings.setInternetHost(it) })
        }
        myHostIp?.let { hip ->
            val clipboard = LocalClipboardManager.current
            val ctx = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hosting at $hip : 51820", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                AppTextButton(onClick = { clipboard.setText(AnnotatedString(hip)) }) { Text("Copy") }
                AppTextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my Mesh Walkie host - IP: $hip  port: 51820")
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
            AppSwitch(checked = netClient, onCheckedChange = { Settings.setInternetClient(it) })
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
