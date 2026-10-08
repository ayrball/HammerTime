package com.hammertime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import com.hammertime.app.domain.model.LapRecord
import com.hammertime.app.domain.model.TrackState
import com.hammertime.app.domain.model.VehicleGForce
import com.hammertime.app.ui.components.GFrictionCircle
import com.hammertime.app.ui.theme.DarkBackground
import com.hammertime.app.ui.theme.DeepGreenBg
import com.hammertime.app.ui.theme.DeepPurpleBg
import com.hammertime.app.ui.theme.DeepRedBg
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonPurple
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.NeonYellow
import java.util.Locale

@Composable
fun TrackDashboardScreen(
    trackState: TrackState,
    speedMph: Double,
    gForce: VehicleGForce,
    isRecording: Boolean,
    satelliteCount: Int,
    onToggleRecording: () -> Unit,
    onSetFinishGate: () -> Unit,
    onCalibrateMount: () -> Unit,
    onResetData: () -> Unit,
    onSwitchToCameraView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // 1. Top Predictive Delta Header Bar (Color-coded Purple / Green / Red with Extra Large Font)
        TopDeltaBanner(
            deltaSec = trackState.deltaSec,
            bestLapTimeSec = trackState.bestLapTimeSec,
            satelliteCount = satelliteCount,
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
            onSwitchToCameraView = onSwitchToCameraView
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Main 3-Column Dashboard Body with Huge Glancing Fonts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Column 1: Lap Timing (Current, Previous, Best)
            LapTimingCard(
                trackState = trackState,
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
            )

            // Column 2: Speedometer & G-Force (Giant Speedo)
            CenterTelemetryCard(
                speedMph = speedMph,
                gForce = gForce,
                trackState = trackState,
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
            )

            // Column 3: Lap History & Peak Stats
            SessionStatsCard(
                trackState = trackState,
                onSetFinishGate = onSetFinishGate,
                onCalibrateMount = onCalibrateMount,
                onResetData = onResetData,
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TopDeltaBanner(
    deltaSec: Float?,
    bestLapTimeSec: Double?,
    satelliteCount: Int,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onSwitchToCameraView: () -> Unit
) {
    val isAhead = (deltaSec ?: 0f) <= 0f
    val isPurplePace = isAhead && deltaSec != null && (bestLapTimeSec == null || deltaSec <= -0.05f)

    val (bannerBg, accentColor, statusLabel) = when {
        deltaSec == null -> Triple(HudCardBg, Color.LightGray, "RECORDING LAP 1 BASELINE")
        isPurplePace -> Triple(DeepPurpleBg, NeonPurple, "PURPLE (FASTEST YET)")
        isAhead -> Triple(DeepGreenBg, NeonGreen, "FASTER")
        else -> Triple(DeepRedBg, NeonRed, "SLOWER")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bannerBg, RoundedCornerShape(12.dp))
            .border(2.dp, accentColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: View Switcher
        Row(
            modifier = Modifier
                .background(HudCardBg, RoundedCornerShape(16.dp))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clickable { onSwitchToCameraView() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("CAMERA HUD", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .background(NeonCyan, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("DASHBOARD", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Black)
            }
        }

        // Center: Giant Delta Display
        if (deltaSec != null) {
            val formatted = String.format(Locale.US, "%+.2fs", deltaSec)
            val indicator = if (isAhead) "▼" else "▲"

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$indicator $formatted",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            }
        } else {
            Text(
                text = "DELTA --.-s ($statusLabel)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray
            )
        }

        // Right: GNSS and REC
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "GNSS $satelliteCount SATS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (satelliteCount >= 8) NeonGreen else NeonYellow
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isRecording) NeonRed else Color.DarkGray, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { onToggleRecording() },
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Box(modifier = Modifier.size(12.dp).background(Color.White, RoundedCornerShape(2.dp)))
                } else {
                    Box(modifier = Modifier.size(14.dp).background(NeonRed, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun LapTimingCard(
    trackState: TrackState,
    modifier: Modifier = Modifier
) {
    val liveTimerColor = when {
        trackState.deltaSec == null -> Color.White
        trackState.deltaSec <= -0.05f -> NeonPurple // Beating session best
        trackState.deltaSec <= 0f -> NeonGreen      // Ahead on pace
        else -> NeonRed                             // Behind on pace
    }

    val lastLap = trackState.lastLapTimeSec
    val bestLap = trackState.bestLapTimeSec
    val (prevLapColor, prevLapBadge) = when {
        lastLap == null -> Pair(Color.LightGray, null)
        bestLap != null && lastLap <= bestLap -> Pair(NeonPurple, "★ PURPLE BEST")
        trackState.lapHistory.size >= 2 && lastLap < trackState.lapHistory[trackState.lapHistory.size - 2].lapTimeSec -> Pair(NeonGreen, "▲ FASTER")
        else -> Pair(NeonRed, "▼ SLOWER")
    }

    Column(
        modifier = modifier
            .background(HudCardBg, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LAP ${trackState.currentLap}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan
                )
                Text(
                    text = if (trackState.finishGateSet) "● GATE SET" else "○ NO GATE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (trackState.finishGateSet) NeonGreen else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Giant Current Lap Timer (50sp bold monospace)
            Text(
                text = formatLapTime(trackState.currentLapTimeSec),
                fontSize = 50.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = liveTimerColor
            )
        }

        Column {
            // Previous Lap with Large Font
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PREVIOUS LAP", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                if (prevLapBadge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(prevLapColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, prevLapColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = prevLapBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = prevLapColor
                        )
                    }
                }
            }
            Text(
                text = lastLap?.let { formatLapTime(it) } ?: "--:--.--",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = prevLapColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Best Lap with Large Purple Font
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BEST LAP", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                if (bestLap != null) {
                    Box(
                        modifier = Modifier
                            .background(NeonPurple.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .border(1.dp, NeonPurple, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("★ PURPLE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NeonPurple)
                    }
                }
            }
            Text(
                text = bestLap?.let { formatLapTime(it) } ?: "--:--.--",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = NeonPurple
            )
        }
    }
}

@Composable
private fun CenterTelemetryCard(
    speedMph: Double,
    gForce: VehicleGForce,
    trackState: TrackState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(HudCardBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Giant Digital Speedometer (74sp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.0f", speedMph),
                    fontSize = 74.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MPH",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            // G-Forces Text
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = String.format(Locale.US, "LAT %+.2fG", gForce.lateralG),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonYellow,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "LONG %+.2fG", gForce.longitudinalG),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // G-Friction Circle
        GFrictionCircle(
            lateralG = gForce.lateralG,
            longitudinalG = gForce.longitudinalG,
            modifier = Modifier.size(95.dp)
        )

        // Corner Apex Live Banner (Large Font)
        if (trackState.isInCorner && trackState.currentCornerApexMph != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF382E00), RoundedCornerShape(8.dp))
                    .border(1.5.dp, NeonYellow, RoundedCornerShape(8.dp))
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.US, "🟡 CORNER APEX: %.1f MPH", trackState.currentCornerApexMph),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = NeonYellow
                )
            }
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun SessionStatsCard(
    trackState: TrackState,
    onSetFinishGate: () -> Unit,
    onCalibrateMount: () -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(HudCardBg, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "CURRENT LAP PEAKS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOP SPEED", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(Locale.US, "%.1f mph", trackState.currentLapTopSpeedMph),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("PEAK LAT G", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(Locale.US, "%.2fG", trackState.currentLapPeakLatG),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = NeonYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lap History Table
            Text(
                text = "SESSION LAPS (${trackState.lapHistory.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (trackState.lapHistory.isEmpty()) {
                Text(
                    text = "No completed laps yet.\nCross finish gate to log laps.",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val history = trackState.lapHistory
                LazyColumn(modifier = Modifier.height(75.dp)) {
                    itemsIndexed(history.asReversed()) { index, lap ->
                        val originalIndex = history.size - 1 - index
                        val previousLapInHistory = if (originalIndex > 0) history[originalIndex - 1] else null
                        LapHistoryRow(lap = lap, priorLap = previousLapInHistory)
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallDashButton("SET GATE", NeonCyan, Modifier.weight(1f)) { onSetFinishGate() }
            SmallDashButton("ZERO", NeonYellow, Modifier.weight(0.8f)) { onCalibrateMount() }
            SmallDashButton("RESET", Color.LightGray, Modifier.weight(0.8f)) { onResetData() }
        }
    }
}

@Composable
private fun LapHistoryRow(lap: LapRecord, priorLap: LapRecord?) {
    val rowColor = when {
        lap.isBest -> NeonPurple
        priorLap != null && lap.lapTimeSec < priorLap.lapTimeSec -> NeonGreen
        else -> NeonRed
    }

    val badge = when {
        lap.isBest -> "★ PURPLE"
        priorLap != null && lap.lapTimeSec < priorLap.lapTimeSec -> "▲ FASTER"
        else -> "▼ SLOWER"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "L${lap.lapNumber} $badge",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = rowColor
        )
        Text(
            text = formatLapTime(lap.lapTimeSec),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = rowColor
        )
        Text(
            text = String.format(Locale.US, "%.0f mph", lap.topSpeedMph),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray
        )
    }
}

@Composable
private fun SmallDashButton(text: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(DarkBackground, RoundedCornerShape(6.dp))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = tint)
    }
}

private fun formatLapTime(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val remSecs = seconds % 60
    return String.format(Locale.US, "%02d:%05.2f", mins, remSecs)
}