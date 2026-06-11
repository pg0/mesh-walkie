package com.meshwalkie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.Settings

/**
 * Quick-text picker: tap to open a dropdown of preset texts (editable in
 * settings), tap one to send. Tiny payload that gets through links where voice
 * fails.
 */
@Composable
fun QuickTextWheel(onSend: (String) -> Unit) {
    val items by Settings.quickTexts.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) { Text("Quick text") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t) },
                    onClick = { onSend(t); expanded = false }
                )
            }
        }
    }
}
