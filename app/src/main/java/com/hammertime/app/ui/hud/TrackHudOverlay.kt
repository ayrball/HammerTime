package com.hammertime.app.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.hammertime.app.ui.components.HudCard
import com.hammertime.app.ui.components.HudLabel
import com.hammertime.app.ui.components.StatusChip
import com.hammertime.app.ui.components.hudSafePadding
import com.hammertime.app.ui.format.formatLapTime
import com.hammertime.app.ui.theme.HudDimens
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonPurple
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.NeonYellow
import com.hammertime.app.ui.theme.TextDim
import com.hammertime.app.ui.theme.TextMuted
import java.util.Locale

@Composable
fun TrackHudOverlay(
    trackState: TrackState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .hudSafePadding()
            .padding(top = HudDimens.belowTopBar - 8.dp)
    ) {
        // Left column: lap card, then corner readouts. The centre of the screen stays clear so the
        // road is never covered.
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LapCard(trackState)
            CornerReadouts(trackState)
        }
    }
}

@Composable
private fun LapCard(trackState: TrackState) {
    val delta = trackState.deltaSec
    val liveTimerColor = when {
        !trackState.finishGateSet -> TextDim
        delta == null -> Color.White
        delta <= -0.05f -> NeonPurple // Beating session best
        delta <= 0f -> NeonGreen      // Ahead on pace
        else -> NeonRed               // Behind pace
    }

    val lastLap = trackState.lastLapTimeSec
    val bestLap = trackState.bestLapTimeSec
    val (lastColor, lastBadge) = when {
        lastLap == null -> Pair(TextMuted, null)
        bestLap != null && lastLap <= bestLap -> Pair(NeonPurple, "★")
        trackState.lapHistory.size >= 2 &&
            lastLap < trackState.lapHistory[trackState.lapHistory.size - 2].lapTimeSec -> Pair(NeonGreen, "▲")
        else -> Pair(NeonRed, "▼")
    }

    HudCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "LAP ${trackState.currentLap}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            color = NeonCyan
        )
        Text(
            text = formatLapTime(trackState.currentLapTimeSec),
            fontSize = 44.sp,
            lineHeight = 48.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = liveTimerColor
        )
        Spacer(modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LapTimeBlock("LAST", lastLap, lastColor, lastBadge)
            LapTimeBlock("BEST", bestLap, NeonPurple, null)
        }
    }
}

@Composable
private fun LapTimeBlock(label: String, seconds: Double?, color: Color, badge: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudLabel(label)
            if (badge != null) {
                Text(text = " $badge", fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
            }
        }
        Text(
            text = seconds?.let { formatLapTime(it) } ?: "--:--.--",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = if (seconds == null) TextDim else color
        )
    }
}

@Composable
private fun CornerReadouts(trackState: TrackState) {
    val apex = trackState.currentCornerApexMph
    val last = trackState.lastCornerSummary

    if (trackState.isInCorner && apex != null) {
        StatusChip(
            text = String.format(Locale.US, "IN CORNER · APEX %.0f MPH", apex),
            color = NeonYellow
        )
    } else if (last != null) {
        Row(
            modifier = Modifier
                .background(Color(0xB3101419), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudLabel("LAST CORNER")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format(
                    Locale.US, "%.0f → %.0f → %.0f mph  %.2fG",
                    last.entrySpeedMph, last.apexMinSpeedMph, last.exitSpeedMph, last.peakLateralG
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }
    }
}
