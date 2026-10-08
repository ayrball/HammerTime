package com.hammertime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.TextDim
import com.hammertime.app.ui.theme.TextMuted
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Live time delta against the best lap. The bar grows left of centre (green) when ahead and right
 * of centre (red) when behind, up to [MAX_VISUAL_SEC].
 */
@Composable
fun PredictiveDeltaBar(
    deltaSec: Float?,
    modifier: Modifier = Modifier
) {
    HudCard(
        modifier = modifier.width(260.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (deltaSec == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudLabel("DELTA")
                Text(
                    text = "  --.--s",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = TextDim
                )
            }
            Text(
                text = "Building baseline lap",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 11.sp,
                color = TextMuted
            )
            return@HudCard
        }

        val isAhead = deltaSec <= 0f
        val color = if (isAhead) NeonGreen else NeonRed

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "%+.2fs", deltaSec),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            Text(
                text = if (isAhead) "  FASTER" else "  SLOWER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }

        val ratio = min(abs(deltaSec) / MAX_VISUAL_SEC, 1f)
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(6.dp)
                .background(TextDim.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
        ) {
            // Each half of the track is 50% of the width.
            val half = 0.5f
            Box(
                modifier = Modifier
                    .align(if (isAhead) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxWidth(half)
                    .height(6.dp)
            ) {
                // Anchored at the centre line, growing outward.
                Box(
                    modifier = Modifier
                        .align(if (isAhead) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxWidth(ratio)
                        .height(6.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .height(10.dp)
                    .background(TextMuted)
            )
        }
    }
}

private const val MAX_VISUAL_SEC = 2.0f
