package com.hammertime.app.domain.model

enum class AppMode {
    DRAG,
    TRACK
}

enum class TrackViewMode {
    CAMERA_HUD,
    FULL_DASHBOARD
}

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedMph: Double,
    val bearingDegrees: Float,
    val accuracyMeters: Float,
    val timestampNanos: Long
)

data class RawImuSample(
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val timestampNanos: Long
)

data class VehicleGForce(
    val lateralG: Float,       // +Right, -Left
    val longitudinalG: Float,  // +Acceleration, -Braking
    val verticalG: Float = 0f
)

data class DragSplitResult(
    val label: String,
    val elapsedSeconds: Double,
    val trapSpeedMph: Double? = null,
    val isCompleted: Boolean = false
)

enum class DragRunStatus {
    WAITING_FOR_STOP,
    ARMED_READY_FOR_LAUNCH,
    LAUNCHED,
    FINISHED
}

data class DragState(
    val status: DragRunStatus = DragRunStatus.WAITING_FOR_STOP,
    val currentSpeedMph: Double = 0.0,
    val peakAccelG: Float = 0f,
    val activeRunSeconds: Double = 0.0,
    val splits: Map<String, DragSplitResult> = emptyMap()
)

data class CornerEvent(
    val entrySpeedMph: Double,
    val apexMinSpeedMph: Double,
    val exitSpeedMph: Double,
    val peakLateralG: Float
)

data class LapRecord(
    val lapNumber: Int,
    val lapTimeSec: Double,
    val isBest: Boolean,
    val topSpeedMph: Double,
    val peakLatG: Float
)

data class TrackState(
    val currentLap: Int = 1,
    val currentLapTimeSec: Double = 0.0,
    val lastLapTimeSec: Double? = null,
    val bestLapTimeSec: Double? = null,
    val deltaSec: Float? = null, // Negative = faster (green), Positive = slower (red)
    val isInCorner: Boolean = false,
    val currentCornerApexMph: Double? = null,
    val lastCornerSummary: CornerEvent? = null,
    val finishGateSet: Boolean = false,
    val lapHistory: List<LapRecord> = emptyList(),
    val currentLapTopSpeedMph: Double = 0.0,
    val currentLapPeakLatG: Float = 0f,
    val currentLapDistanceMeters: Double = 0.0
)

data class TelemetryFrame(
    val timestampNanos: Long,
    val speedMph: Double,
    val gForce: VehicleGForce,
    val gpsPoint: GpsPoint?,
    val isRecording: Boolean,
    val mode: AppMode
)