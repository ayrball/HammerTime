package com.hammertime.app.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.hammertime.app.domain.model.AppMode
import com.hammertime.app.domain.model.DragState
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.model.TrackState
import com.hammertime.app.domain.model.TrackViewMode
import com.hammertime.app.domain.model.VehicleGForce
import com.hammertime.app.ui.components.CameraPreview
import com.hammertime.app.ui.components.GFrictionCircle
import com.hammertime.app.ui.components.PredictiveDeltaBar
import com.hammertime.app.ui.components.SpeedometerDisplay
import com.hammertime.app.ui.hud.DragHudOverlay
import com.hammertime.app.ui.hud.TrackHudOverlay
import com.hammertime.app.ui.theme.HudCardBg
import com.hammertime.app.ui.theme.NeonCyan
import com.hammertime.app.ui.theme.NeonGreen
import com.hammertime.app.ui.theme.NeonRed
import com.hammertime.app.ui.theme.NeonYellow
import java.util.Locale

@Composable
fun MainTelemetryScreen(
    currentMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    trackViewMode: TrackViewMode,
    onTrackViewModeChange: (TrackViewMode) -> Unit,
    speedMph: Double,
    gForce: VehicleGForce,
    dragState: DragState,
    trackState: TrackState,
    gpsPoint: GpsPoint?,
    satelliteCount: Int,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onCalibrateMount: () -> Unit,
    onSetFinishGate: () -> Unit,
    onResetData: () -> Unit,
    onPreviewViewReady: (PreviewView) -> Unit
) {
    if (currentMode == AppMode.TRACK && trackViewMode == TrackViewMode.FULL_DASHBOARD) {
        TrackDashboardScreen(
            trackState = trackState,
            speedMph = speedMph,
            gForce = gForce,
            isRecording = isRecording,
            satelliteCount = satelliteCount,
            onToggleRecording = onToggleRecording,
            onSetFinishGate = onSetFinishGate,
            onCalibrateMount = onCalibrateMount,
            onResetData = onResetData,
            onSwitchToCameraView = { onTrackViewModeChange(TrackViewMode.CAMERA_HUD) }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(onPreviewViewCreated = onPreviewViewReady)

        when (currentMode) {
            AppMode.DRAG -> DragHudOverlay(
                dragState = dragState,
                modifier = Modifier.fillMaxSize()
            )
            AppMode.TRACK -> TrackHudOverlay(
                trackState = trackState,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(HudCardBg, RoundedCornerShape(20.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeTab("DRAG", currentMode == AppMode.DRAG) { onModeChange(AppMode.DRAG) }
                ModeTab("TRACK", currentMode == AppMode.TRACK) { onModeChange(AppMode.TRACK) }

                if (currentMode == AppMode.TRACK) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.DarkGray, RoundedCornerShape(12.dp))
                            .clickable { onTrackViewModeChange(TrackViewMode.FULL_DASHBOARD) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("DASH 📊", fontSize = 11.sp, color = NeonYellow, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (currentMode == AppMode.TRACK) {
                PredictiveDeltaBar(deltaSec = trackState.deltaSec)
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentMode == AppMode.TRACK) {
                    SmallActionButton("SET GATE", NeonCyan) { onSetFinishGate() }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                SmallActionButton("ZERO MOUNT", NeonYellow) { onCalibrateMount() }
                Spacer(modifier = Modifier.width(6.dp))
                SmallActionButton("RESET", Color.LightGray) { onResetData() }
                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .background(HudCardBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "GNSS $satelliteCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (satelliteCount >= 8) NeonGreen else NeonYellow
                    )
                    Text(
                        text = gpsPoint?.let { String.format(Locale.US, "±%.1fm", it.accuracyMeters) } ?: "NO LOCK",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (isRecording) NeonRed else Color.DarkGray, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { onToggleRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(NeonRed, CircleShape)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            GFrictionCircle(
                lateralG = gForce.lateralG,
                longitudinalG = gForce.longitudinalG,
                modifier = Modifier
                    .size(105.dp)
                    .background(HudCardBg, RoundedCornerShape(12.dp))
                    .padding(6.dp)
            )

            SpeedometerDisplay(
                speedMph = speedMph,
                lateralG = gForce.lateralG,
                longitudinalG = gForce.longitudinalG
            )

            Box(
                modifier = Modifier
                    .size(105.dp, 48.dp)
                    .background(HudCardBg, RoundedCornerShape(10.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRecording) "REC ● 1080P" else "STANDBY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) NeonRed else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ModeTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) NeonCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = if (selected) Color.Black else Color.LightGray
        )
    }
}

@Composable
private fun SmallActionButton(title: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(HudCardBg, RoundedCornerShape(8.dp))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}