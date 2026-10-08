package com.hammertime.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hammertime.app.data.camera.CameraVideoRecorder
import com.hammertime.app.data.location.InternalGpsProvider
import com.hammertime.app.data.sensor.ImuSensorProvider
import com.hammertime.app.domain.drag.DragTimingEngine
import com.hammertime.app.domain.fusion.ImuMountCalibrator
import com.hammertime.app.domain.fusion.SpeedSensorFusion
import com.hammertime.app.domain.model.AppMode
import com.hammertime.app.domain.model.DragState
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.model.TrackState
import com.hammertime.app.domain.model.TrackViewMode
import com.hammertime.app.domain.model.VehicleGForce
import com.hammertime.app.domain.track.TrackTimingEngine
import com.hammertime.app.ui.screens.MainTelemetryScreen
import com.hammertime.app.ui.theme.HammerTimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var gpsProvider: InternalGpsProvider
    private lateinit var imuProvider: ImuSensorProvider
    private lateinit var cameraRecorder: CameraVideoRecorder

    private val mountCalibrator = ImuMountCalibrator()
    private val sensorFusion = SpeedSensorFusion()
    private val dragEngine = DragTimingEngine()
    private val trackEngine = TrackTimingEngine()

    private var currentMode by mutableStateOf(AppMode.DRAG)
    private var trackViewMode by mutableStateOf(TrackViewMode.CAMERA_HUD)
    private var speedMph by mutableDoubleStateOf(0.0)
    private var gForce by mutableStateOf(VehicleGForce(0f, 0f, 0f))
    private var dragState by mutableStateOf(DragState())
    private var trackState by mutableStateOf(TrackState())
    private var latestGpsPoint by mutableStateOf<GpsPoint?>(null)
    private var satelliteCount by mutableIntStateOf(0)
    private var isRecording by mutableStateOf(false)
    private var hasPermissions by mutableStateOf(false)

    private var previewView: PreviewView? = null
    private var sensorsStarted = false

    // Engines run on every sensor sample; Compose state and the sidecar are rate-limited.
    private var lastUiPublishNanos = 0L
    private var lastSidecarNanos = 0L

    private companion object {
        const val UI_PUBLISH_INTERVAL_NANOS = 33_000_000L
        const val SIDECAR_INTERVAL_NANOS = 20_000_000L
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasPermissions = cameraGranted && locationGranted
        if (hasPermissions) {
            setupCameraAndSensors()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during driving & hide status bars for immersive dashcam HUD
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        gpsProvider = InternalGpsProvider(this)
        imuProvider = ImuSensorProvider(this)
        cameraRecorder = CameraVideoRecorder(this)

        checkAndRequestPermissions()

        setContent {
            HammerTimeTheme {
                if (hasPermissions) {
                    MainTelemetryScreen(
                        currentMode = currentMode,
                        onModeChange = { currentMode = it },
                        trackViewMode = trackViewMode,
                        onTrackViewModeChange = { trackViewMode = it },
                        speedMph = speedMph,
                        gForce = gForce,
                        dragState = dragState,
                        trackState = trackState,
                        gpsPoint = latestGpsPoint,
                        satelliteCount = satelliteCount,
                        isRecording = isRecording,
                        onToggleRecording = { toggleRecording() },
                        onCalibrateMount = {
                            if (!mountCalibrator.calibrateWithGravityVector(imuProvider.currentGravityVector)) {
                                toast("Can't zero mount: hold the phone upright with the camera facing forward")
                            }
                        },
                        onSetFinishGate = {
                            val gps = latestGpsPoint
                            if (gps == null || !trackEngine.setFinishGateAtCurrentLocation(gps)) {
                                toast("Set the gate while moving (3+ mph) with a GPS lock")
                            }
                        },
                        onResetData = {
                            dragEngine.manualReset()
                            trackEngine.reset()
                            sensorFusion.reset()
                            dragState = DragState()
                            trackState = TrackState()
                        },
                        onPreviewViewReady = { pView ->
                            previewView = pView
                            cameraRecorder.bindCamera(this@MainActivity, pView)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { checkAndRequestPermissions() }) {
                            Text("Grant Camera & GPS Permissions")
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Camera and fine location are required to run; audio is optional (video is silent without it).
        val essential = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        val all = essential + arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

        if (essential.all(::granted)) {
            hasPermissions = true
            setupCameraAndSensors()
        } else {
            permissionLauncher.launch(all)
        }
    }

    private fun setupCameraAndSensors() {
        previewView?.let { cameraRecorder.bindCamera(this, it) }

        if (sensorsStarted) return
        sensorsStarted = true

        // Sensors only run while the activity is visible; collection restarts on resume.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectImu() }
                launch { collectGps() }
            }
        }
    }

    /** High-rate IMU (50-500 Hz): integrate speed, feed the engines, log telemetry. */
    private suspend fun collectImu() {
        imuProvider.observeImu()
            .flowOn(Dispatchers.Default)
            .collect { imu ->
                val vehicleG = mountCalibrator.transformToVehicleG(imu.accelX, imu.accelY, imu.accelZ)

                val forwardAccMs2 = vehicleG.longitudinalG * 9.80665f
                val currentSpeed = sensorFusion.onImuUpdate(forwardAccMs2, imu.timestampNanos)

                val drag = if (currentMode == AppMode.DRAG) {
                    dragEngine.processFrame(currentSpeed, vehicleG.longitudinalG, imu.timestampNanos)
                } else null
                val track = if (currentMode == AppMode.TRACK) {
                    trackEngine.processFrame(latestGpsPoint, currentSpeed, vehicleG.lateralG, imu.timestampNanos)
                } else null

                if (imu.timestampNanos - lastUiPublishNanos >= UI_PUBLISH_INTERVAL_NANOS) {
                    lastUiPublishNanos = imu.timestampNanos
                    gForce = vehicleG
                    speedMph = currentSpeed
                    if (drag != null) dragState = drag
                    if (track != null) trackState = track
                }

                if (isRecording && imu.timestampNanos - lastSidecarNanos >= SIDECAR_INTERVAL_NANOS) {
                    lastSidecarNanos = imu.timestampNanos
                    cameraRecorder.logTelemetryPoint(
                        String.format(
                            Locale.US,
                            "{\"t\":%d,\"spd\":%.2f,\"latG\":%.2f,\"longG\":%.2f}",
                            imu.timestampNanos, currentSpeed, vehicleG.lateralG, vehicleG.longitudinalG
                        )
                    )
                }
            }
    }

    /** ~1-10 Hz GPS: correct IMU drift with Doppler speed and feed position to the engines. */
    private suspend fun collectGps() {
        gpsProvider.observeLocation()
            .flowOn(Dispatchers.Default)
            .collect { gps ->
                latestGpsPoint = gps
                satelliteCount = gpsProvider.satelliteCount

                val correctedSpeed = sensorFusion.onGpsUpdate(gps.speedMph, gps.accuracyMeters)
                speedMph = correctedSpeed

                // Fixes are timestamped when measured, which lags delivery. Frame time must be "now"
                // (same clock as the IMU) or the lap clock steps backwards at every fix.
                val now = SystemClock.elapsedRealtimeNanos()
                if (currentMode == AppMode.DRAG) {
                    dragState = dragEngine.processFrame(correctedSpeed, gForce.longitudinalG, now)
                } else {
                    trackState = trackEngine.processFrame(gps, correctedSpeed, gForce.lateralG, now)
                }
            }
    }

    private fun toggleRecording() {
        if (isRecording) {
            cameraRecorder.stopRecording()
            isRecording = false
            return
        }

        val mediaDir = externalMediaDirs.firstOrNull() ?: filesDir
        val started = cameraRecorder.startRecording(mediaDir) { event ->
            if (event is VideoRecordEvent.Finalize) {
                // Recording ended (stopped, camera unbound, storage full...): keep the UI honest.
                isRecording = false
                if (event.hasError()) toast("Recording stopped unexpectedly")
            }
        }
        if (started == null) {
            toast("Camera isn't ready yet")
        } else {
            isRecording = true
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraRecorder.stopRecording()
    }
}
