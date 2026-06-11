package com.meshwalkie.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.service.Settings
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Genshin-style radial wheel. Press and hold the trigger: the ring appears
 * centred on your thumb. Drag toward a preset to highlight it, release to send.
 * Release near the centre (dead zone) cancels. One gesture, no second tap.
 */
@Composable
fun QuickTextWheel(onSend: (String) -> Unit) {
    val items by Settings.quickTexts.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }

    val n = items.size.coerceAtLeast(1)
    val sector = 360.0 / n

    Box(
        modifier = Modifier
            .pointerInput(items) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val deadZone = 36f
                awaitEachGesture {
                    awaitFirstDown()
                    open = true
                    selected = -1
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val v = change.position - center
                        selected = if (v.getDistance() < deadZone) {
                            -1
                        } else {
                            var deg = Math.toDegrees(atan2(v.x.toDouble(), -v.y.toDouble()))
                            if (deg < 0) deg += 360.0
                            Math.round(deg / sector).toInt().mod(n)
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                    open = false
                    val sel = selected
                    if (sel in items.indices) onSend(items[sel])
                    selected = -1
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Quick text", style = MaterialTheme.typography.titleMedium)
    }

    if (open) {
        // Centre the ring on the trigger (the thumb).
        val provider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset = IntOffset(
                    anchorBounds.center.x - popupContentSize.width / 2,
                    anchorBounds.center.y - popupContentSize.height / 2
                )
            }
        }
        Popup(
            popupPositionProvider = provider,
            properties = PopupProperties(focusable = false, clippingEnabled = false)
        ) {
            Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                val radius = 120f
                items.forEachIndexed { i, text ->
                    val angle = Math.toRadians(-90.0 + i * sector)
                    val highlighted = i == selected
                    Surface(
                        shape = CircleShape,
                        color = if (highlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.offset(
                            x = (radius * cos(angle)).dp,
                            y = (radius * sin(angle)).dp
                        )
                    ) {
                        Text(
                            text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (highlighted) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0x55000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(if (selected == -1) "cancel" else "↑", color = Color.White) }
            }
        }
    }
}
