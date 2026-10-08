package com.hammertime.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hammertime.app.ui.theme.NeonYellow
import com.hammertime.app.ui.theme.TextMuted
import java.util.Locale

@Composable
fun SpeedometerDisplay(
    speedMph: Double,
    lateralG: Float,
    longitudinalG: Float,
    modifier: Modifier = Modifier
) {
    HudCard(
        // Fixed minimum width so the card doesn't resize when the speed gains a digit.
        modifier = modifier.widthIn(min = 230.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = String.format(Locale.US, "%.0f", speedMph),
                fontSize = 68.sp,
                lineHeight = 72.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MPH",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = String.format(Locale.US, "LAT %+.2fG", lateralG),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NeonYellow,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(Locale.US, "LONG %+.2fG", longitudinalG),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
