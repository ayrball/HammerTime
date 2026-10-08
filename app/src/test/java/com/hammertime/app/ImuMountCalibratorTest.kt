package com.hammertime.app

import com.hammertime.app.domain.fusion.ImuMountCalibrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImuMountCalibratorTest {

    private val g = 9.80665f

    /**
     * Landscape phone, perfectly upright on the dash, camera facing forward.
     * Phone frame: +X = screen-right (vehicle right when facing the driver is mirrored, but the
     * camera looks forward so +X is vehicle-right), +Y = up, +Z = out of the screen toward driver.
     */
    @Test
    fun uprightMount_forwardIsMinusZ_rightIsPlusX() {
        val cal = ImuMountCalibrator()
        assertTrue(cal.calibrateWithGravityVector(floatArrayOf(0f, g, 0f)))

        // Car accelerates forward at 0.5g: phone sees acceleration along -Z.
        val accel = cal.transformToVehicleG(0f, 0f, -0.5f * g)
        assertEquals(0.5f, accel.longitudinalG, 0.001f)
        assertEquals(0f, accel.lateralG, 0.001f)

        // Car corners right at 0.8g: phone sees +X.
        val corner = cal.transformToVehicleG(0.8f * g, 0f, 0f)
        assertEquals(0.8f, corner.lateralG, 0.001f)
        assertEquals(0f, corner.longitudinalG, 0.001f)
    }

    @Test
    fun tiltedMount_forwardAccelerationStillMapsToLongitudinal() {
        val cal = ImuMountCalibrator()
        // Phone tilted back 30 degrees from vertical: gravity ("up") has a +Z component.
        val tilt = Math.toRadians(30.0)
        val up = floatArrayOf(0f, Math.cos(tilt).toFloat(), Math.sin(tilt).toFloat())
        assertTrue(cal.calibrateWithGravityVector(floatArrayOf(up[0] * g, up[1] * g, up[2] * g)))

        // The camera axis (0, 0, -1) with its component along "up" removed, normalised, is
        // (0, sin, -cos): that is the horizontal forward direction in phone axes.
        val fwd = floatArrayOf(0f, Math.sin(tilt).toFloat(), -Math.cos(tilt).toFloat())
        val a = 0.6f * g
        val out = cal.transformToVehicleG(fwd[0] * a, fwd[1] * a, fwd[2] * a)

        assertEquals(0.6f, out.longitudinalG, 0.001f)
        assertEquals(0f, out.lateralG, 0.001f)
        assertEquals(0f, out.verticalG, 0.001f)
    }

    @Test
    fun gravityComponentIsRemovedFromHorizontalAxes() {
        val cal = ImuMountCalibrator()
        val tilt = Math.toRadians(40.0)
        val up = floatArrayOf(0f, Math.cos(tilt).toFloat(), Math.sin(tilt).toFloat())
        cal.calibrateWithGravityVector(floatArrayOf(up[0] * g, up[1] * g, up[2] * g))

        // Raw accelerometer (gravity included) while stationary must read 0g horizontally.
        val still = cal.transformToVehicleG(up[0] * g, up[1] * g, up[2] * g)
        assertEquals(0f, still.longitudinalG, 0.001f)
        assertEquals(0f, still.lateralG, 0.001f)
        assertEquals(1f, still.verticalG, 0.001f)
    }

    @Test
    fun brakingIsNegativeAndLeftIsNegative() {
        val cal = ImuMountCalibrator()
        cal.calibrateWithGravityVector(floatArrayOf(0f, g, 0f))
        val out = cal.transformToVehicleG(-0.3f * g, 0f, 0.9f * g)
        assertEquals(-0.3f, out.lateralG, 0.001f)
        assertEquals(-0.9f, out.longitudinalG, 0.001f)
    }

    @Test
    fun phoneLyingFlat_isRejectedAndKeepsPreviousCalibration() {
        val cal = ImuMountCalibrator()
        assertTrue(cal.calibrateWithGravityVector(floatArrayOf(0f, g, 0f)))
        // Lying flat: gravity is along Z so the camera axis gives no horizontal forward direction.
        assertFalse(cal.calibrateWithGravityVector(floatArrayOf(0f, 0f, g)))
        assertFalse(cal.calibrateWithGravityVector(floatArrayOf(0f, 0f, 0f)))

        val out = cal.transformToVehicleG(0f, 0f, -0.5f * g)
        assertEquals(0.5f, out.longitudinalG, 0.001f)
    }
}
