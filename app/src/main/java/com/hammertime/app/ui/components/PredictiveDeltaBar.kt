package com.hammertime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonRed
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@Composable
fun PredictiveDeltaBar(
    deltaSec: Float?,
    modifier: Modifier = Modifier
) {
    if (deltaSec == null) {
        Box(
            modifier = modifier
                .background(HudCardBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "DELTA --.-s (RECORDING LAP 1)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val isAhead = deltaSec <= 0f
    val barColor = if (isAhead) NeonGreen else NeonRed
    val formatted = String.format(Locale.US, "%+.2fs", deltaSec)
    val label = if (isAhead) "FASTER" else "SLOWER"

    val maxVisualSec = 2.0f
    val ratio = min(abs(deltaSec) / maxVisualSec, 1.0f)

    Box(
        modifier = modifier
            .width(200.dp)
            .background(HudCardBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "DELTA ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )
            Text(
                text = "$formatted $label",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = barColor
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(ratio)
                .height(2.5.dp)
                .background(barColor, RoundedCornerShape(1.dp))
        )
    }
}