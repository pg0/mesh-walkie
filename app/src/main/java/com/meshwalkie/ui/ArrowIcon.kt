package com.meshwalkie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Arrow pointing up at rotationDeg = 0; rotated by bearingToPeer - myHeading. */
@Composable
fun ArrowIcon(rotationDeg: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(40.dp)) {
        rotate(degrees = rotationDeg) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)   // tip
                lineTo(w * 0.82f, h * 0.85f)  // right tail
                lineTo(w * 0.5f, h * 0.65f)   // notch
                lineTo(w * 0.18f, h * 0.85f)  // left tail
                close()
            }
            drawPath(path, color = Color(0xFF1565C0))
            drawCircle(
                color = Color(0x331565C0),
                radius = w * 0.5f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
        }
    }
}
