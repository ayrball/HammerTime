package com.hammertime.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hammertime.app.ui.theme.NeonYellow
import kotlin.math.min

@Composable
fun GFrictionCircle(
    lateralG: Float,
    longitudinalG: Float,
    modifier: Modifier = Modifier,
    maxG: Float = 1.5f
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 4f

        // Draw outer 1.5G ring
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = radius,
            style = Stroke(width = 2f)
        )
        // Draw 1.0G ring
        drawCircle(
            color = Color.White.copy(alpha = 0.40f),
            radius = radius * (1.0f / maxG),
            style = Stroke(width = 2f)
        )
        // Draw 0.5G ring
        drawCircle(
            color = Color.White.copy(alpha = 0.20f),
            radius = radius * (0.5f / maxG),
            style = Stroke(width = 1.5f)
        )

        // Draw crosshairs
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.5f
        )

        // Calculate dot position (Forward acceleration goes UP, Right turn goes RIGHT)
        val clampedLatG = lateralG.coerceIn(-maxG, maxG)
        val clampedLongG = longitudinalG.coerceIn(-maxG, maxG)

        val dotX = center.x + (clampedLatG / maxG) * radius
        val dotY = center.y - (clampedLongG / maxG) * radius

        // Draw current G position dot
        drawCircle(
            color = NeonYellow,
            radius = 9f,
            center = Offset(dotX, dotY)
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = Offset(dotX, dotY)
        )
    }
}
