package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Press-and-hold: onPtt(true) on press, onPtt(false) on release/cancel. */
@Composable
fun PttButton(onPtt: (pressed: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .background(
                color = if (held) Color(0xFFD32F2F) else Color(0xFF388E3C),
                shape = CircleShape
            )
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
            tint = Color.White,
            modifier = Modifier.size(56.dp)
        )
    }
}
