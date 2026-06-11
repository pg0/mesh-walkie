package com.meshwalkie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.Settings
import kotlin.math.cos
import kotlin.math.sin

/**
 * Genshin-style radial shortcut wheel for preset quick-texts (editable in
 * settings). Tap to open the ring, tap a preset to send it. A tiny payload that
 * gets through links where voice fails.
 */
@Composable
fun QuickTextWheel(onSend: (String) -> Unit) {
    val items by Settings.quickTexts.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    TextButton(onClick = { open = true }) { Text("Quick text") }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                val n = items.size.coerceAtLeast(1)
                val radius = 110f
                items.forEachIndexed { i, text ->
                    val angle = Math.toRadians(-90.0 + i * 360.0 / n)
                    Button(
                        onClick = { onSend(text); open = false },
                        modifier = Modifier.offset(
                            x = (radius * cos(angle)).dp,
                            y = (radius * sin(angle)).dp
                        )
                    ) { Text(text) }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        TextButton(onClick = { open = false }) { Text("✕") }
                    }
                }
            }
        }
    }
}
