package com.hammertime.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Stat(val label: String, val value: String, val color: Color = Color.White)

/** Compact two-value readout that balances the G-circle on the other side of the speedometer. */
@Composable
fun PeakStatsCard(first: Stat, second: Stat? = null, modifier: Modifier = Modifier) {
    HudCard(
        modifier = modifier.widthIn(min = 128.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            StatBlock(first)
            if (second != null) {
                Spacer(modifier = Modifier.height(6.dp))
                StatBlock(second)
            }
        }
    }
}

@Composable
private fun StatBlock(stat: Stat) {
    Column(horizontalAlignment = Alignment.End) {
        HudLabel(stat.label)
        Text(
            text = stat.value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = stat.color
        )
    }
}
