package com.hammertime.app.domain.fusion

import kotlin.math.max

class SpeedSensorFusion {
    var fusedSpeedMph: Double = 0.0
        private set

    private var lastImuTimestampNanos: Long = 0L
    private val MS_TO_MPH = 2.23693629

    /**
     * Process IMU forward acceleration at high rate (50-100Hz)
     * @param longitudinalAccMps2 forward acceleration in m/s^2
     */
    fun onImuUpdate(longitudinalAccMps2: Float, timestampNanos: Long): Double {
        if (lastImuTimestampNanos == 0L) {
            lastImuTimestampNanos = timestampNanos
            return fusedSpeedMph
        }

        val dtSec = (timestampNanos - lastImuTimestampNanos) / 1_000_000_000.0
        lastImuTimestampNanos = timestampNanos

        // Integrate acceleration: delta_v = a * dt
        val deltaSpeedMph = (longitudinalAccMps2 * dtSec) * MS_TO_MPH

        // Filter tiny sensor noise jitter around zero
        val threshold = 0.02 * dtSec * MS_TO_MPH
        if (Math.abs(deltaSpeedMph) > threshold) {
            fusedSpeedMph = max(0.0, fusedSpeedMph + deltaSpeedMph)
        }

        return fusedSpeedMph
    }

    /**
     * Correct speed with GPS Doppler velocity update (1-5Hz).
     */
    fun onGpsUpdate(gpsSpeedMph: Double, accuracyMeters: Float): Double {
        if (accuracyMeters > 20.0f) {
            // Poor GPS lock, rely primarily on integrated IMU
            return fusedSpeedMph
        }

        // If vehicle is virtually stopped according to GPS, reset IMU drift
        if (gpsSpeedMph < 0.3) {
            fusedSpeedMph = 0.0
            return 0.0
        }

        // Complementary filter: GPS Doppler velocity provides absolute reference
        val alpha = 0.75
        fusedSpeedMph = alpha * fusedSpeedMph + (1.0 - alpha) * gpsSpeedMph
        return fusedSpeedMph
    }

    fun reset() {
        fusedSpeedMph = 0.0
        lastImuTimestampNanos = 0L
    }
}
