package com.hammertime.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DarkBackground = Color(0xFF0D0F12)

// Cards float over live camera video, so they are fairly opaque and carry a hairline border to
// stay legible against both bright sky and dark tarmac.
val HudCardBg = Color(0xE0101419)
val HudBorder = Color(0x26FFFFFF)

val NeonGreen = Color(0xFF00E676)
val NeonRed = Color(0xFFFF1744)
val NeonYellow = Color(0xFFFFD600)
val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFFD500F9)
val DeepPurpleBg = Color(0xFF2E0042)
val DeepGreenBg = Color(0xFF003818)
val DeepRedBg = Color(0xFF380008)
val DeepYellowBg = Color(0xFF382E00)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9E9E9E)
val TextMuted = Color(0xFF8F9AA9)
val TextDim = Color(0xFF566070)

object HudShapes {
    val card = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)
}

object HudDimens {
    /** Gap between the screen edge (after cutout insets) and HUD content. */
    val edge = 16.dp
    val gap = 10.dp

    /** Minimum touch target for anything tappable while the phone is mounted in a moving car. */
    val touchTarget = 48.dp

    /** Y offset where HUD cards start, just below the top control bar. */
    val belowTopBar = 72.dp
}

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
