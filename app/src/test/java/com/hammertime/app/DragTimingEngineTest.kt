package com.hammertime.app

import com.hammertime.app.domain.drag.DragTimingEngine
import com.hammertime.app.domain.model.DragRunStatus
import com.hammertime.app.ui.hud.displayedRunSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DragTimingEngineTest {

    private lateinit var engine: DragTimingEngine

    @Before
    fun setUp() {
        engine = DragTimingEngine()
    }

    @Test
    fun testVehicleStationary_armsWhenStopped() {
        // Vehicle stopped
        val state1 = engine.processFrame(0.0, 0f, 1_000_000_000L)
        assertEquals(DragRunStatus.ARMED_READY_FOR_LAUNCH, state1.status)
        assertEquals(0.0, state1.activeRunSeconds, 0.001)
    }

    @Test
    fun testLaunchDetection_triggersOnSpeedOrG() {
        // Step 1: Arm
        engine.processFrame(0.0, 0f, 1_000_000_000L)

        // Step 2: Launch with 0.8 mph and 0.45G
        val stateLaunch = engine.processFrame(0.8, 0.45f, 1_100_000_000L)
        assertEquals(DragRunStatus.LAUNCHED, stateLaunch.status)
        assertEquals(0.45f, stateLaunch.peakAccelG, 0.01f)
    }

    @Test
    fun testSubSampleInterpolation_0to60Accurate() {
        // Arm
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)

        // Launch
        val t1 = t0 + 100_000_000L // +0.1s
        engine.processFrame(1.0, 0.8f, t1)

        // Accelerate through 30 mph:
        // Sample before 30: 28.0 mph at t = t1 + 1.8s
        val t2 = t1 + 1_800_000_000L
        engine.processFrame(28.0, 0.6f, t2)

        // Sample after 30: 32.0 mph at t = t1 + 2.0s
        val t3 = t1 + 2_000_000_000L
        val state30 = engine.processFrame(32.0, 0.55f, t3)

        // 30 is exactly halfway between 28 and 32 (ratio = 0.5)
        // Crossing time should be t2 + 0.5 * 0.2s = 1.9s from launch
        val split30 = state30.splits["0-30"]
        assertNotNull("0-30 split should be recorded", split30)
        assertEquals(1.90, split30!!.elapsedSeconds, 0.02)

        // Accelerate through 60 mph:
        // Sample before 60: 58.0 mph at t = t1 + 3.9s
        val t4 = t1 + 3_900_000_000L
        engine.processFrame(58.0, 0.5f, t4)

        // Sample after 60: 62.0 mph at t = t1 + 4.1s
        val t5 = t1 + 4_100_000_000L
        val state60 = engine.processFrame(62.0, 0.45f, t5)

        // 60 is halfway between 58 and 62 (ratio = 0.5)
        // Crossing time = 4.0s from launch
        val split60 = state60.splits["0-60"]
        assertNotNull("0-60 split should be recorded", split60)
        assertEquals(4.00, split60!!.elapsedSeconds, 0.02)
    }

    @Test
    fun testQuarterMileSplit_recordedWithTrapSpeed() {
        // Arm
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)
        var currentTime = t0 + 100_000_000L

        // Launch and simulate acceleration covering full 1/4 mile (402.34m)
        var currentSpeed = 10.0
        var state = engine.processFrame(currentSpeed, 0.5f, currentTime)
        while (state.splits["1/4 Mile"] == null && currentSpeed < 140.0) {
            currentSpeed = kotlin.math.min(130.0, currentSpeed + 4.0)
            currentTime += 500_000_000L // 0.5s ticks
            state = engine.processFrame(currentSpeed, 0.5f, currentTime)
        }

        val splitQuarter = state.splits["1/4 Mile"]
        assertNotNull("1/4 Mile split should be recorded", splitQuarter)
        assertTrue("Trap speed should be > 100 mph", (splitQuarter?.trapSpeedMph ?: 0.0) > 100.0)
    }

    @Test
    fun testFalseStart_bumpWithoutMotionAbortsRun() {
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)

        // A bump spikes longitudinal G and launches the run...
        var state = engine.processFrame(0.0, 0.5f, t0 + 100_000_000L)
        assertEquals(DragRunStatus.LAUNCHED, state.status)

        // ...but the car never moves. After the grace period the run is abandoned.
        state = engine.processFrame(0.0, 0f, t0 + 1_000_000_000L)
        assertEquals(DragRunStatus.LAUNCHED, state.status)
        state = engine.processFrame(0.0, 0f, t0 + 2_500_000_000L)
        assertEquals(DragRunStatus.WAITING_FOR_STOP, state.status)

        // And it re-arms for the next run.
        state = engine.processFrame(0.0, 0f, t0 + 2_600_000_000L)
        assertEquals(DragRunStatus.ARMED_READY_FOR_LAUNCH, state.status)
        assertEquals(0.0, state.activeRunSeconds, 0.001)
    }

    @Test
    fun testSlowRollingStart_isNotAbortedWithinGracePeriod() {
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)
        engine.processFrame(0.7, 0.35f, t0 + 100_000_000L)
        // Still under 2 mph for 1s, then pulling away: must remain a live run.
        var state = engine.processFrame(1.5, 0.3f, t0 + 1_100_000_000L)
        assertEquals(DragRunStatus.LAUNCHED, state.status)
        state = engine.processFrame(20.0, 0.5f, t0 + 2_000_000_000L)
        assertEquals(DragRunStatus.LAUNCHED, state.status)
    }

    @Test
    fun testFinishedRun_keepsSplitsUntilNextArm() {
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)
        var time = t0 + 100_000_000L
        var speed = 10.0
        var state = engine.processFrame(speed, 0.5f, time)
        while (state.status != DragRunStatus.FINISHED && speed < 140.0) {
            speed = kotlin.math.min(130.0, speed + 4.0)
            time += 500_000_000L
            state = engine.processFrame(speed, 0.5f, time)
        }
        assertEquals(DragRunStatus.FINISHED, state.status)
        assertNotNull(state.splits["1/4 Mile"])
        assertNotNull(state.splits["0-60"])
    }

    @Test
    fun testRunDistance_reportedWhileLaunched_andResetOnRearm() {
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)
        engine.processFrame(10.0, 0.5f, t0 + 100_000_000L)

        // Speed ramps 10 -> 22.3694 mph over 1 s: average 16.18 mph = 7.235 m/s, so 7.235 m.
        val state = engine.processFrame(22.3694, 0.5f, t0 + 1_100_000_000L)
        assertEquals(7.235, state.distanceMeters, 0.01)

        engine.manualReset()
        val rearmed = engine.processFrame(0.0, 0f, t0 + 5_000_000_000L)
        assertEquals(0.0, rearmed.distanceMeters, 0.001)
    }

    @Test
    fun testDisplayedRunSeconds_holdsQuarterMileEtAfterFinish() {
        val t0 = 1_000_000_000L
        engine.processFrame(0.0, 0f, t0)
        var time = t0 + 100_000_000L
        var speed = 10.0
        var state = engine.processFrame(speed, 0.5f, time)
        var live = 0.0
        while (state.status != DragRunStatus.FINISHED && speed < 140.0) {
            speed = kotlin.math.min(130.0, speed + 4.0)
            time += 500_000_000L
            state = engine.processFrame(speed, 0.5f, time)
            if (state.status == DragRunStatus.LAUNCHED) live = state.displayedRunSeconds()
        }

        assertEquals(DragRunStatus.FINISHED, state.status)
        assertEquals("engine reports 0 once finished", 0.0, state.activeRunSeconds, 0.0)
        assertEquals(state.splits["1/4 Mile"]!!.elapsedSeconds, state.displayedRunSeconds(), 1e-9)
        assertTrue("final ET should be at least the last live reading", state.displayedRunSeconds() >= live)
    }
}
