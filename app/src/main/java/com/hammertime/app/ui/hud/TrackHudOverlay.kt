package com.hammertime.app.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.hammertime.app.domain.model.TrackState
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonPurple
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.NeonYellow
import java.util.Locale

@Composable
fun TrackHudOverlay(
    trackState: TrackState,
    modifier: Modifier = Modifier
) {
    val liveTimerColor = when {
        trackState.deltaSec == null -> Color.White
        trackState.deltaSec <= -0.05f -> NeonPurple // Beating session best (Purple)
        trackState.deltaSec <= 0f -> NeonGreen      // Ahead on pace (Green)
        else -> NeonRed                             // Behind pace (Red)
    }

    val lastLap = trackState.lastLapTimeSec
    val bestLap = trackState.bestLapTimeSec
    val (prevLapColor, prevLapBadge) = when {
        lastLap == null -> Pair(Color.LightGray, null)
        bestLap != null && lastLap <= bestLap -> Pair(NeonPurple, "★ PURPLE")
        trackState.lapHistory.size >= 2 && lastLap < trackState.lapHistory[trackState.lapHistory.size - 2].lapTimeSec -> Pair(NeonGreen, "▲ FASTER")
        else -> Pair(NeonRed, "▼ SLOWER")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 68.dp)
    ) {
        // Left Card: Current Lap and Times
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(HudCardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LAP ${trackState.currentLap}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (trackState.finishGateSet) "● GATE SET" else "○ NO GATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (trackState.finishGateSet) NeonGreen else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Big Live Current Lap Timer (36sp)
            Text(
                text = formatLapTime(trackState.currentLapTimeSec),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = liveTimerColor
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LAST ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(
                    text = lastLap?.let { formatLapTime(it) } ?: "--:--.--",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = prevLapColor
                )
                if (prevLapBadge != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = prevLapBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = prevLapColor
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text("BEST ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(
                    text = bestLap?.let { formatLapTime(it) } ?: "--:--.--",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = NeonPurple
                )
            }
        }

        // Center: Corner Apex Speed Banner
        if (trackState.isInCorner && trackState.currentCornerApexMph != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(HudCardBg, RoundedCornerShape(10.dp))
                    .border(2.dp, NeonYellow, RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "🟡 CORNER APEX: %.1f MPH", trackState.currentCornerApexMph),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = NeonYellow
                )
            }
        } else if (trackState.lastCornerSummary != null) {
            val c = trackState.lastCornerSummary
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(HudCardBg.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "PREV CORNER: IN %.0f -> APEX %.0f -> OUT %.0f MPH (%.2fG)",
                        c.entrySpeedMph, c.apexMinSpeedMph, c.exitSpeedMph, c.peakLateralG
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray
                )
            }
        }
    }
}

private fun formatLapTime(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val remSecs = seconds % 60
    return String.format(Locale.US, "%02d:%05.2f", mins, remSecs)
}