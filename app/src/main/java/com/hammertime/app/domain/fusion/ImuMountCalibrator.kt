package com.hammertime.app.domain.fusion

import com.hammertime.app.domain.model.VehicleGForce
import kotlin.math.sqrt

/**
 * Maps phone-frame acceleration into the vehicle frame.
 *
 * The phone is assumed to be dash-mounted with the screen facing the driver and the back camera
 * facing forward, so the vehicle's forward direction is the phone's -Z axis (projected onto the
 * horizontal plane). "Up" comes from the gravity sensor, which reads +1g along the direction the
 * phone considers "up". Lateral (right) is forward x up.
 *
 * Because the basis is built from gravity and the camera axis, it works for any pitch or roll of
 * the mount and for either landscape orientation.
 */
class ImuMountCalibrator {
    private var up = floatArrayOf(0f, 0.707f, 0.707f)
    private var forward = floatArrayOf(0f, 0f, 0f)
    private var right = floatArrayOf(0f, 0f, 0f)

    var isCalibrated = false
        private set

    init {
        // Default guess: landscape, screen facing driver, tilted back about 45 degrees.
        buildBasis(up)
    }

    /**
     * @param gravity gravity-sensor reading in the phone frame (m/s^2, points "up" in phone axes)
     * @return false if the vector was unusable (zero length, or phone lying flat so the camera axis
     *   is parallel to gravity and no forward direction can be derived)
     */
    fun calibrateWithGravityVector(gravity: FloatArray): Boolean {
        val upUnit = normalize(floatArrayOf(gravity[0], gravity[1], gravity[2])) ?: return false
        if (!buildBasis(upUnit)) return false
        isCalibrated = true
        return true
    }

    private fun buildBasis(upUnit: FloatArray): Boolean {
        // Camera axis (-Z) with its vertical component removed.
        val camDotUp = -upUnit[2]
        val fwd = normalize(
            floatArrayOf(
                -camDotUp * upUnit[0],
                -camDotUp * upUnit[1],
                -1f - camDotUp * upUnit[2]
            )
        ) ?: return false

        up = upUnit
        forward = fwd
        right = cross(fwd, upUnit)
        return true
    }

    /**
     * Projects phone-frame acceleration (m/s^2) onto vehicle axes, returned in G:
     * - lateral: +right / -left
     * - longitudinal: +forward / -braking
     * - vertical: +up
     */
    fun transformToVehicleG(accelX: Float, accelY: Float, accelZ: Float): VehicleGForce {
        val gravityMs2 = 9.80665f
        val ax = accelX / gravityMs2
        val ay = accelY / gravityMs2
        val az = accelZ / gravityMs2

        return VehicleGForce(
            lateralG = dot(ax, ay, az, right),
            longitudinalG = dot(ax, ay, az, forward),
            verticalG = dot(ax, ay, az, up)
        )
    }

    private fun dot(x: Float, y: Float, z: Float, axis: FloatArray) =
        x * axis[0] + y * axis[1] + z * axis[2]

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    private fun normalize(v: FloatArray): FloatArray? {
        val norm = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
        if (norm < 1e-3f) return null
        return floatArrayOf(v[0] / norm, v[1] / norm, v[2] / norm)
    }
}
