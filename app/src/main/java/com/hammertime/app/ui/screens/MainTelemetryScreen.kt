package com.hammertime.app.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hammertime.app.domain.model.AppMode
import com.hammertime.app.domain.model.DragState
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.model.TrackState
import com.hammertime.app.domain.model.TrackViewMode
import com.hammertime.app.domain.model.VehicleGForce
import com.hammertime.app.ui.components.CameraPreview
import com.hammertime.app.ui.components.GFrictionCircle
import com.hammertime.app.ui.components.HudCard
import com.hammertime.app.ui.components.PeakStatsCard
import com.hammertime.app.ui.components.PredictiveDeltaBar
import com.hammertime.app.ui.components.SpeedometerDisplay
import com.hammertime.app.ui.components.Stat
import com.hammertime.app.ui.components.StatusChip
import com.hammertime.app.ui.components.TopControlBar
import com.hammertime.app.ui.components.hudSafePadding
import com.hammertime.app.ui.hud.DragHudOverlay
import com.hammertime.app.ui.hud.DragStatusChip
import com.hammertime.app.ui.hud.TrackHudOverlay
import com.hammertime.app.ui.theme.HudDimens
import com.hammertime.app.ui.theme.NeonCyan
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
    recordingSeconds: Int,
    mountZeroed: Boolean,
    onToggleRecording: () -> Unit,
    onCalibrateMount: () -> Unit,
    onSetFinishGate: () -> Unit,
    onResetData: () -> Unit,
    onPreviewViewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
    // Overridable so layouts can be rendered without a real camera (see UiSnapshotTest).
    cameraContent: @Composable () -> Unit = { CameraPreview(onPreviewViewCreated = onPreviewViewReady) }
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

    val isTrack = currentMode == AppMode.TRACK

    Box(modifier = modifier.fillMaxSize()) {
        cameraContent()

        when (currentMode) {
            AppMode.DRAG -> DragHudOverlay(dragState = dragState, modifier = Modifier.fillMaxSize())
            AppMode.TRACK -> TrackHudOverlay(trackState = trackState, modifier = Modifier.fillMaxSize())
        }

        TopControlBar(
            currentMode = currentMode,
            onModeChange = onModeChange,
            gpsPoint = gpsPoint,
            satelliteCount = satelliteCount,
            isRecording = isRecording,
            recordingSeconds = recordingSeconds,
            onToggleRecording = onToggleRecording,
            onShowDashboard = if (isTrack) ({ onTrackViewModeChange(TrackViewMode.FULL_DASHBOARD) }) else null,
            onSetFinishGate = if (isTrack) onSetFinishGate else null,
            onCalibrateMount = onCalibrateMount,
            onResetData = onResetData,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hudSafePadding(),
            center = {
                if (isTrack) PredictiveDeltaBar(deltaSec = trackState.deltaSec)
                else DragStatusChip(status = dragState.status)
            }
        )

        // Actionable nudges live just under the top bar and disappear once resolved.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hudSafePadding()
                .padding(top = HudDimens.belowTopBar + 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTrack && !trackState.finishGateSet) {
                StatusChip("SET FINISH GATE", NeonCyan, icon = Icons.Filled.Flag, onClick = onSetFinishGate)
            }
            if (!mountZeroed) {
                StatusChip("TAP TO ZERO MOUNT", NeonYellow, icon = Icons.Filled.Tune, onClick = onCalibrateMount)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .hudSafePadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            HudCard(contentPadding = PaddingValues(8.dp)) {
                GFrictionCircle(
                    lateralG = gForce.lateralG,
                    longitudinalG = gForce.longitudinalG,
                    modifier = Modifier.size(92.dp)
                )
            }

            SpeedometerDisplay(
                speedMph = speedMph,
                lateralG = gForce.lateralG,
                longitudinalG = gForce.longitudinalG
            )

            if (isTrack) {
                PeakStatsCard(
                    first = Stat("TOP SPEED", String.format(Locale.US, "%.0f mph", trackState.currentLapTopSpeedMph)),
                    second = Stat("PEAK LAT", String.format(Locale.US, "%.2f G", trackState.currentLapPeakLatG), NeonYellow)
                )
            } else {
                // One stat only: the 1/4 mile ET is already in the timer and splits card, and the
                // splits card needs the vertical room on shorter phones.
                PeakStatsCard(
                    first = Stat("PEAK ACCEL", String.format(Locale.US, "%.2f G", dragState.peakAccelG), NeonYellow)
                )
            }
        }
    }
}
