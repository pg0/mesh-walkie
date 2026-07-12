package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.core.AppTheme
import com.meshwalkie.service.MeshBus
import com.meshwalkie.service.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Press-and-hold: onPtt(true) on press, onPtt(false) on release/cancel. */
@Composable
fun PttButton(onPtt: (pressed: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    // armed = the recorder actually started during this hold; guards against an
    // amber flash in the brief gap between press and the async recording=true.
    var armed by remember { mutableStateOf(false) }
    val theme by Settings.theme.collectAsStateWithLifecycle()
    val night = theme == AppTheme.NIGHT
    val recording by MeshBus.recording.collectAsStateWithLifecycle()
    LaunchedEffect(held, recording) {
        if (held && recording) armed = true
        if (!held) armed = false
    }
    // Shape/frame/size are theme structure - reads LocalAppTheme directly
    // rather than the "theme" var above (which only drives the night-vision color flag).
    val appTheme = LocalAppTheme.current
    val maxedColor = if (night) Color(0xFF7A4A00) else Color(0xFFF9A825)  // auto-sent at cap, still held
    // Finger down, recorder already stopped = hit the max-duration cap and sent.
    // FIELD reads as the reference's flat red REC bar (held = pressed ink);
    // CORRUPTION as a framed paper tile whose red mic fills red while held;
    // the round themes keep the classic green-idle/red-held knob.
    val color = when (appTheme) {
        AppTheme.FIELD -> when {
            held && armed && !recording -> maxedColor
            held -> Color(0xFF1B1B1B)
            else -> Color(0xFFE23B2E)
        }
        AppTheme.CORRUPTION -> when {
            held && armed && !recording -> maxedColor
            held -> Color(0xFFB3403E)
            else -> MaterialTheme.colorScheme.background
        }
        else -> when {
            held && armed && !recording -> maxedColor
            held -> if (night) Color(0xFFD0342C) else Color(0xFFD32F2F)
            else -> if (night) Color(0xFF5A0A0A) else Color(0xFF388E3C)
        }
    }
    val knobShape: Shape = when (appTheme) {
        AppTheme.FIELD -> RoundedCornerShape(3.dp)
        AppTheme.CORRUPTION -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    val ringModifier = when (appTheme) {
        AppTheme.CORRUPTION -> Modifier.border(2.dp, MaterialTheme.colorScheme.outline, knobShape)
        else -> Modifier
    }
    // Bar/tile footprint on the paper themes (button-row scale, per the
    // references); the classic knob stays a 120dp circle.
    val sizeModifier = when (appTheme) {
        AppTheme.FIELD -> Modifier.size(width = 168.dp, height = 60.dp)
        AppTheme.CORRUPTION -> Modifier.size(width = 148.dp, height = 64.dp)
        else -> Modifier.size(120.dp)
    }
    val iconTint = when {
        appTheme == AppTheme.CORRUPTION && !held -> Color(0xFFB3403E)   // red record dot on paper
        else -> Color.White
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(sizeModifier)
            .background(color = color, shape = knobShape)
            .then(ringModifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        held = true
                        onPtt(true)
                        tryAwaitRelease()   // suspends until finger lifts or cancels
                        held = false
                        onPtt(false)
                    }
                )
            }
    ) {
        Icon(
            painter = painterResource(android.R.drawable.ic_btn_speak_now),
            contentDescription = "Push to talk",
            tint = iconTint,
            modifier = Modifier.size(40.dp)
        )
    }
}
