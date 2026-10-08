package com.hammertime.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import com.hammertime.app.domain.model.AppMode
import com.hammertime.app.domain.model.CornerEvent
import com.hammertime.app.domain.model.DragRunStatus
import com.hammertime.app.domain.model.DragSplitResult
import com.hammertime.app.domain.model.DragState
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.model.LapRecord
import com.hammertime.app.domain.model.TrackState
import com.hammertime.app.domain.model.TrackViewMode
import com.hammertime.app.domain.model.VehicleGForce
import com.hammertime.app.ui.screens.MainTelemetryScreen
import com.hammertime.app.ui.screens.PermissionScreen
import com.hammertime.app.ui.theme.HammerTimeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNGs under app/build/ui-snapshots so layouts can be reviewed without
 * a device. 891x411dp is a typical phone in landscape (Pixel 10a). These tests only assert that
 * rendering succeeds; the images are for humans.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class UiSnapshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Bright sky over tarmac: the worst case for legibility of translucent cards. */
    @Composable
    private fun FakeCamera() {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF8DB4E2),
                        0.45f to Color(0xFFE3ECF6),
                        0.46f to Color(0xFF5A5C60),
                        1.0f to Color(0xFF2E2F33)
                    )
                )
        )
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        // Infinite pulse animations would otherwise keep the test clock busy forever.
        compose.mainClock.autoAdvance = false
        compose.setContent { HammerTimeTheme { content() } }
        compose.mainClock.advanceTimeBy(500)
        // captureToImage() waits on a window-capture callback Robolectric never fires; draw the view directly.
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        val dir = File("build/ui-snapshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val gps = GpsPoint(37.77, -122.41, 10.0, 62.0, 90f, 1.8f, 1L)

    private val laps = listOf(
        LapRecord(1, 92.31, false, 118.0, 1.02f),
        LapRecord(2, 90.84, false, 121.4, 1.11f),
        LapRecord(3, 89.97, true, 123.0, 1.18f)
    )

    private val trackRunning = TrackState(
        currentLap = 4, currentLapTimeSec = 47.35, lastLapTimeSec = 89.97, bestLapTimeSec = 89.97,
        deltaSec = -0.42f, finishGateSet = true, lapHistory = laps,
        currentLapTopSpeedMph = 118.4, currentLapPeakLatG = 1.12f, currentLapDistanceMeters = 1850.0,
        isInCorner = false, currentCornerApexMph = null,
        lastCornerSummary = CornerEvent(96.0, 58.3, 71.0, 1.12f)
    )

    private val dragRunning = DragState(
        status = DragRunStatus.LAUNCHED, currentSpeedMph = 74.0, peakAccelG = 0.82f,
        activeRunSeconds = 5.62, distanceMeters = 165.0,
        splits = mapOf(
            "0-30" to DragSplitResult("0-30", 1.84, 30.0, true),
            "0-60" to DragSplitResult("0-60", 4.71, 60.0, true)
        )
    )

    private val dragFinished = DragState(
        status = DragRunStatus.FINISHED, currentSpeedMph = 98.0, peakAccelG = 0.82f,
        activeRunSeconds = 0.0, distanceMeters = 402.4,
        splits = mapOf(
            "0-30" to DragSplitResult("0-30", 1.84, 30.0, true),
            "0-60" to DragSplitResult("0-60", 4.71, 60.0, true),
            "0-100" to DragSplitResult("0-100", 11.9, 100.0, true),
            "1/8 Mile" to DragSplitResult("1/8 Mile", 8.2, 78.4, true),
            "1/4 Mile" to DragSplitResult("1/4 Mile", 12.9, 101.3, true)
        )
    )

    @Composable
    private fun Main(
        mode: AppMode,
        view: TrackViewMode = TrackViewMode.CAMERA_HUD,
        drag: DragState = DragState(),
        track: TrackState = TrackState(),
        speed: Double = 74.0,
        g: VehicleGForce = VehicleGForce(0.35f, 0.62f),
        recording: Boolean = false,
        zeroed: Boolean = true,
        gpsFix: GpsPoint? = gps
    ) {
        MainTelemetryScreen(
            currentMode = mode, onModeChange = {}, trackViewMode = view, onTrackViewModeChange = {},
            speedMph = speed, gForce = g, dragState = drag, trackState = track, gpsPoint = gpsFix,
            satelliteCount = if (gpsFix == null) 0 else 14, isRecording = recording,
            recordingSeconds = 125, mountZeroed = zeroed, onToggleRecording = {},
            onCalibrateMount = {}, onSetFinishGate = {}, onResetData = {}, onPreviewViewReady = {},
            cameraContent = { FakeCamera() }
        )
    }

    @Test fun drag_waiting_unzeroed_nolock() = snapshot("drag_waiting_unzeroed_nolock") {
        Main(AppMode.DRAG, speed = 22.0, g = VehicleGForce(0f, 0f), zeroed = false, gpsFix = null)
    }

    @Test fun drag_armed() = snapshot("drag_armed") {
        Main(AppMode.DRAG, drag = DragState(status = DragRunStatus.ARMED_READY_FOR_LAUNCH), speed = 0.0, g = VehicleGForce(0f, 0f))
    }

    @Test fun drag_running() = snapshot("drag_running") { Main(AppMode.DRAG, drag = dragRunning, recording = true) }

    @Test fun drag_finished() = snapshot("drag_finished") { Main(AppMode.DRAG, drag = dragFinished, speed = 98.0) }

    @Test fun track_hud() = snapshot("track_hud") {
        Main(AppMode.TRACK, track = trackRunning, speed = 96.0, g = VehicleGForce(1.05f, -0.2f), recording = true)
    }

    @Test fun track_hud_in_corner_behind() = snapshot("track_hud_in_corner_behind") {
        Main(
            AppMode.TRACK,
            track = trackRunning.copy(deltaSec = 0.85f, isInCorner = true, currentCornerApexMph = 58.3),
            speed = 61.0, g = VehicleGForce(1.15f, -0.1f), recording = true
        )
    }

    @Test fun track_hud_no_gate() = snapshot("track_hud_no_gate") {
        Main(AppMode.TRACK, track = TrackState(), speed = 0.0, g = VehicleGForce(0f, 0f), zeroed = false)
    }

    @Test fun track_dashboard() = snapshot("track_dashboard") {
        Main(AppMode.TRACK, view = TrackViewMode.FULL_DASHBOARD, track = trackRunning, speed = 96.0,
            g = VehicleGForce(1.05f, -0.2f), recording = true)
    }

    @Test fun permission_screen() = snapshot("permission_screen") { PermissionScreen(onGrant = {}) }
}

/** The same screens on a shorter (360dp tall) phone, to catch vertical collisions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w740dp-h360dp-land-xhdpi")
class UiSnapshotCompactTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compact_drag_finished() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HammerTimeTheme {
                MainTelemetryScreen(
                    currentMode = AppMode.DRAG, onModeChange = {}, trackViewMode = TrackViewMode.CAMERA_HUD,
                    onTrackViewModeChange = {}, speedMph = 74.0, gForce = VehicleGForce(0.35f, 0.62f),
                    dragState = DragState(
                        status = DragRunStatus.FINISHED, distanceMeters = 402.4,
                        splits = mapOf(
                            "0-30" to DragSplitResult("0-30", 1.84, 30.0, true),
                            "1/8 Mile" to DragSplitResult("1/8 Mile", 8.2, 78.4, true),
                            "1/4 Mile" to DragSplitResult("1/4 Mile", 12.9, 101.3, true)
                        )
                    ),
                    trackState = TrackState(), gpsPoint = null, satelliteCount = 9, isRecording = true,
                    recordingSeconds = 42, mountZeroed = false, onToggleRecording = {}, onCalibrateMount = {},
                    onSetFinishGate = {}, onResetData = {}, onPreviewViewReady = {},
                    cameraContent = { Box(Modifier.fillMaxSize().background(Color(0xFF5A6A80))) }
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        val dir = File("build/ui-snapshots").apply { mkdirs() }
        File(dir, "compact_drag_finished.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
