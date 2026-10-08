package com.hammertime.app

import com.hammertime.app.domain.fusion.SpeedSensorFusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeedSensorFusionTest {

    private lateinit var fusion: SpeedSensorFusion

    @Before
    fun setUp() {
        fusion = SpeedSensorFusion()
    }

    @Test
    fun testImuIntegration_integratesForwardAcceleration() {
        var time = 1_000_000_000L
        fusion.onImuUpdate(0f, time)

        // 1G forward acceleration for 1 second (9.80665 m/s^2)
        // Expected speed delta = ~21.93 mph (1 m/s = 2.23694 mph)
        for (i in 1..10) {
            time += 100_000_000L // 100ms
            fusion.onImuUpdate(9.80665f, time)
        }

        assertTrue("Fused speed should be ~21.9 mph", fusion.fusedSpeedMph in 21.0..23.0)
    }

    @Test
    fun testGpsDopplerCorrection_correctsDrift() {
        // High IMU integration drift up to 30 mph
        fusion.onImuUpdate(10f, 1_000_000_000L)
        fusion.onImuUpdate(10f, 2_000_000_000L)

        // GPS update indicates true Doppler speed is 20 mph with high accuracy
        fusion.onGpsUpdate(20.0, 1.5f)

        // Filter should pull fused speed toward 20 mph
        assertTrue("Speed should adjust toward GPS Doppler reference", fusion.fusedSpeedMph < 25.0)
    }

    @Test
    fun testLargeImuGap_isNotIntegrated() {
        fusion.onImuUpdate(0f, 1_000_000_000L)
        // 60 s gap (stream paused) with a 1G sample must not add ~1300 mph.
        fusion.onImuUpdate(9.80665f, 61_000_000_000L)
        assertEquals(0.0, fusion.fusedSpeedMph, 0.001)

        // Integration resumes normally afterwards.
        fusion.onImuUpdate(9.80665f, 61_100_000_000L)
        assertTrue(fusion.fusedSpeedMph in 2.0..2.4)
    }
}
