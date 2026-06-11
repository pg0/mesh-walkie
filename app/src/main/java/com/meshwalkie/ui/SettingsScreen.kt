package com.meshwalkie.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.Settings

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // System back returns to the main screen instead of closing the app.
    BackHandler { onBack() }

    val dark by Settings.darkMode.collectAsStateWithLifecycle()
    val savedName by Settings.displayName.collectAsStateWithLifecycle()
    val savedGroup by Settings.groupCode.collectAsStateWithLifecycle()

    var nameField by remember { mutableStateOf(savedName) }
    var groupField by remember { mutableStateOf(savedGroup) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Back") }
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

        Text("Group (same code = same mesh)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = groupField,
            onValueChange = { groupField = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Only devices with the same code connect. Share a private code for a private group.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
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

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                Settings.setDisplayName(nameField)
                // setGroupCode returns true when it changed; the service observes
                // the flow and rejoins the new mesh automatically.
                Settings.setGroupCode(groupField)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        Spacer(Modifier.height(24.dp))

        Text(
            "Device ID: ${Settings.deviceId}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
