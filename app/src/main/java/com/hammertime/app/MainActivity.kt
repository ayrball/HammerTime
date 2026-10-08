package com.hammertime.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
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
import java.io.File
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
                            mountCalibrator.calibrateWithGravityVector(imuProvider.currentGravityVector)
                        },
                        onSetFinishGate = {
                            latestGpsPoint?.let { trackEngine.setFinishGateAtCurrentLocation(it) }
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
        val required = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            hasPermissions = true
            setupCameraAndSensors()
        } else {
            permissionLauncher.launch(required)
        }
    }

    private fun setupCameraAndSensors() {
        previewView?.let { cameraRecorder.bindCamera(this, it) }

        // Observe High-rate IMU (50-100Hz)
        lifecycleScope.launch {
            imuProvider.observeImu()
                .flowOn(Dispatchers.Default)
                .collect { imu ->
                    val vehicleG = mountCalibrator.transformToVehicleG(
                        imu.accelX, imu.accelY, imu.accelZ
                    )
                    gForce = vehicleG

                    // Integrate acceleration into speed
                    val forwardAccMs2 = vehicleG.longitudinalG * 9.80665f
                    val currentSpeed = sensorFusion.onImuUpdate(forwardAccMs2, imu.timestampNanos)
                    speedMph = currentSpeed

                    // Feed timing engines
                    if (currentMode == AppMode.DRAG) {
                        dragState = dragEngine.processFrame(currentSpeed, vehicleG.longitudinalG, imu.timestampNanos)
                    } else {
                        trackState = trackEngine.processFrame(latestGpsPoint, currentSpeed, vehicleG.lateralG, imu.timestampNanos)
                    }

                    // Write telemetry to sidecar file if recording
                    if (isRecording) {
                        val sidecarJson = String.format(
                            Locale.US,
                            "{\"t\":%d,\"spd\":%.2f,\"latG\":%.2f,\"longG\":%.2f}",
                            imu.timestampNanos, currentSpeed, vehicleG.lateralG, vehicleG.longitudinalG
                        )
                        cameraRecorder.logTelemetryPoint(sidecarJson)
                    }
                }
        }

        // Observe High-rate Internal GPS
        lifecycleScope.launch {
            gpsProvider.observeLocation()
                .flowOn(Dispatchers.Default)
                .collect { gps ->
                    latestGpsPoint = gps
                    satelliteCount = gpsProvider.satelliteCount

                    // Correct speed drift with GPS Doppler velocity
                    val correctedSpeed = sensorFusion.onGpsUpdate(gps.speedMph, gps.accuracyMeters)
                    speedMph = correctedSpeed

                    if (currentMode == AppMode.DRAG) {
                        dragState = dragEngine.processFrame(correctedSpeed, gForce.longitudinalG, gps.timestampNanos)
                    } else {
                        trackState = trackEngine.processFrame(gps, correctedSpeed, gForce.lateralG, gps.timestampNanos)
                    }
                }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            cameraRecorder.stopRecording()
            isRecording = false
        } else {
            val mediaDir = externalMediaDirs.firstOrNull() ?: filesDir
            cameraRecorder.startRecording(mediaDir) { event ->
                // Handled in CameraVideoRecorder
            }
            isRecording = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraRecorder.stopRecording()
    }
}
