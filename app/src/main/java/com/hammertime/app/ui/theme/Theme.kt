package com.hammertime.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0D0F12)
val HudCardBg = Color(0xCC14181F)
val NeonGreen = Color(0xFF00E676)
val NeonRed = Color(0xFFFF1744)
val NeonYellow = Color(0xFFFFD600)
val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFFD500F9)
val DeepPurpleBg = Color(0xFF2E0042)
val DeepGreenBg = Color(0xFF003818)
val DeepRedBg = Color(0xFF380008)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9E9E9E)

private val ColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonGreen,
    tertiary = NeonYellow,
    background = DarkBackground,
    surface = HudCardBg,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun HammerTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}