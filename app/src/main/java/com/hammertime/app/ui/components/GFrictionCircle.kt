package com.hammertime.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.NeonYellow
import kotlin.math.hypot
import kotlin.math.min

/**
 * Friction circle: forward acceleration moves the dot up, braking down, a right turn right.
 * The dot warms from cyan to red as total G approaches the limit.
 */
@Composable
fun GFrictionCircle(
    lateralG: Float,
    longitudinalG: Float,
    modifier: Modifier = Modifier,
    maxG: Float = 1.5f
) {
    val total = hypot(lateralG, longitudinalG)
    val dotColor = when {
        total < 0.5f -> NeonCyan
        total < 1.0f -> NeonYellow
        else -> NeonRed
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "G force %.2f lateral, %.2f longitudinal".format(lateralG, longitudinalG)
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 4f

        // Soft disc so the rings read as one instrument over busy video.
        drawCircle(color = Color.White.copy(alpha = 0.06f), radius = radius, center = center)

        // 0.5G, 1.0G and outer (maxG) rings; the 1G ring is the one that matters, so it is brightest.
        drawCircle(Color.White.copy(alpha = 0.18f), radius * (0.5f / maxG), center, style = Stroke(1.5f))
        drawCircle(Color.White.copy(alpha = 0.45f), radius * (1.0f / maxG), center, style = Stroke(2f))
        drawCircle(Color.White.copy(alpha = 0.25f), radius, center, style = Stroke(2f))

        // Crosshairs
        val axis = Color.White.copy(alpha = 0.22f)
        drawLine(axis, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.5f)
        drawLine(axis, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.5f)

        val dotX = center.x + (lateralG.coerceIn(-maxG, maxG) / maxG) * radius
        val dotY = center.y - (longitudinalG.coerceIn(-maxG, maxG) / maxG) * radius

        drawCircle(dotColor.copy(alpha = 0.28f), radius = 20f, center = Offset(dotX, dotY))
        drawCircle(dotColor, radius = 10f, center = Offset(dotX, dotY))
        drawCircle(Color.White, radius = 4f, center = Offset(dotX, dotY))
    }
}
