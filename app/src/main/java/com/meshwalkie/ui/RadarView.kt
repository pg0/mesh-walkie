package com.meshwalkie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import com.meshwalkie.core.Display
import com.meshwalkie.core.Freshness
import com.meshwalkie.core.PeerView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tiles-free, internet-free position view for off-grid use. Heading-up radar:
 * "up" is the direction the phone faces (consistent with the arrow list). Peers
 * are plotted from the distance + bearing we already compute. Range auto-scales
 * to the farthest peer (min 200 m). No map provider, no API key, works offline.
 */
@Composable
fun RadarView(peers: List<PeerView>, myHeadingDeg: Float, modifier: Modifier = Modifier) {
    val gridArgb = Color(0xFF4A5A5E).toArgb()
    val labelArgb = Color(0xFFB9C6C9).toArgb()
    val meColor = Color(0xFF4FC3F7)

    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = labelArgb
            textSize = 34f
            isAntiAlias = true
        }
    }
    val ringPaint = remember {
        android.graphics.Paint().apply {
            color = gridArgb
            textSize = 28f
            isAntiAlias = true
        }
    }

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = min(cx, cy) * 0.88f

        val farthest = peers.maxOfOrNull { it.distanceMeters } ?: 0.0
        val maxDist = maxOf(farthest, 200.0)

        // range rings + distance labels
        val rings = 4
        for (k in 1..rings) {
            val rr = maxR * k / rings
            drawCircle(
                color = Color(0xFF3A4A4E),
                radius = rr,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
            val ringDist = maxDist * k / rings
            drawContext.canvas.nativeCanvas.drawText(
                Display.formatDistance(ringDist), cx + 6f, cy - rr + 26f, ringPaint
            )
        }
        // cross hairs
        drawLine(Color(0xFF2C3A3E), Offset(cx, cy - maxR), Offset(cx, cy + maxR), strokeWidth = 1.5f)
        drawLine(Color(0xFF2C3A3E), Offset(cx - maxR, cy), Offset(cx + maxR, cy), strokeWidth = 1.5f)

        // me at center
        drawCircle(meColor, radius = 12f, center = Offset(cx, cy))
        drawContext.canvas.nativeCanvas.drawText("you", cx + 18f, cy + 10f, labelPaint)

        // peers
        peers.forEach { p ->
            val angle = Math.toRadians(p.bearingDeg - myHeadingDeg)  // up = my heading
            val rr = (min(p.distanceMeters, maxDist) / maxDist).toFloat() * maxR
            val x = cx + (rr * sin(angle)).toFloat()
            val y = cy - (rr * cos(angle)).toFloat()
            val color = when (p.freshness) {
                Freshness.FRESH -> Color(0xFF2E7D32)
                Freshness.AGING -> Color(0xFFF9A825)
                Freshness.STALE -> Color(0xFFC62828)
            }
            drawCircle(color, radius = 16f, center = Offset(x, y))
            drawContext.canvas.nativeCanvas.drawText(
                "${p.name} ${Display.formatDistance(p.distanceMeters)}",
                x + 20f, y + 10f, labelPaint
            )
        }
    }
}
