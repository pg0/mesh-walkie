package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.Settings
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radial shortcut wheel for preset quick-texts (editable in settings). Opens as
 * a full-screen centered overlay (Genshin-style central wheel) so it never jumps
 * around the screen. Tap a preset to send, tap the scrim or centre to cancel.
 */
@Composable
fun QuickTextWheel(onSend: (String) -> Unit) {
    val items by Settings.quickTexts.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    TextButton(onClick = { open = true }) { Text("Quick text") }

    if (open) {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { open = false },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                    val n = items.size.coerceAtLeast(1)
                    val radius = 120f
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
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        TextButton(onClick = { open = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
