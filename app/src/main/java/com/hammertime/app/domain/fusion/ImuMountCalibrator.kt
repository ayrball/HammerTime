package com.hammertime.app.domain.fusion

import com.hammertime.app.domain.model.VehicleGForce
import kotlin.math.sqrt

class ImuMountCalibrator {
    // Default calibration assumes phone in landscape, screen facing driver, tilted back slightly
    private var gravityUnit = floatArrayOf(0f, 0.707f, 0.707f)
    private var isCalibrated = false

    fun calibrateWithGravityVector(gravity: FloatArray) {
        val norm = sqrt((gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble()).toFloat()
        if (norm > 1e-3f) {
            gravityUnit[0] = gravity[0] / norm
            gravityUnit[1] = gravity[1] / norm
            gravityUnit[2] = gravity[2] / norm
            isCalibrated = true
        }
    }

    /**
     * Projects phone raw linear acceleration onto vehicle coordinates:
     * - Longitudinal (Forward/Backward)
     * - Lateral (Left/Right)
     * - Vertical
     */
    fun transformToVehicleG(
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        isLandscape: Boolean = true
    ): VehicleGForce {
        val GRAVITY_MS2 = 9.80665f

        // Convert m/s^2 to G
        val ax = accelX / GRAVITY_MS2
        val ay = accelY / GRAVITY_MS2
        val az = accelZ / GRAVITY_MS2

        return if (isLandscape) {
            // In landscape on dashboard:
            // X is vehicle forward/backward or lateral depending on orientation
            // Y is lateral or vertical
            // Z is through the screen
            // If calibrated, we remove the vertical projection along gravityUnit
            val dot = ax * gravityUnit[0] + ay * gravityUnit[1] + az * gravityUnit[2]
            val vertG = dot
            // Horizontal planar acceleration components
            val horizX = ax - dot * gravityUnit[0]
            val horizY = ay - dot * gravityUnit[1]

            // In landscape mount:
            // Longitude (Forward acceleration / Braking) is along Y or X
            VehicleGForce(
                lateralG = horizX,
                longitudinalG = horizY,
                verticalG = vertG
            )
        } else {
            VehicleGForce(
                lateralG = ax,
                longitudinalG = ay,
                verticalG = az
            )
        }
    }
}
