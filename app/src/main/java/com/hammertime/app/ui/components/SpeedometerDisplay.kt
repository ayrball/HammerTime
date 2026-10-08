package com.hammertime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.hammertime.app.ui.theme.NeonYellow
import java.util.Locale

@Composable
fun SpeedometerDisplay(
    speedMph: Double,
    lateralG: Float,
    longitudinalG: Float,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(HudCardBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.US, "%.0f", speedMph),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "MPH",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format(Locale.US, "LAT %+.2fG", lateralG),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NeonYellow,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = String.format(Locale.US, "LONG %+.2fG", longitudinalG),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
