package com.hammertime.app

import com.hammertime.app.domain.fusion.SpeedSensorFusion
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.track.TrackTimingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackTimingEngineTest {

    private lateinit var engine: TrackTimingEngine

    @Before
    fun setUp() {
        engine = TrackTimingEngine()
    }

    @Test
    fun testGateSettingAndCrossing_completesLap() {
        val startLoc = GpsPoint(
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 10.0,
            speedMph = 45.0,
            bearingDegrees = 0f, // heading due North
            accuracyMeters = 2.0f,
            timestampNanos = 1_000_000_000L
        )

        // Set gate
        engine.setFinishGateAtCurrentLocation(startLoc)

        // Drive out around the track (simulating ~200m traveled)
        var time = 1_000_000_000L
        var lat = 37.7749
        for (i in 1..20) {
            time += 1_000_000_000L // 1s ticks
            lat += 0.0001
            val point = GpsPoint(lat, -122.4194, 10.0, 50.0, 0f, 2.0f, time)
            engine.processFrame(point, 50.0, 0f, time)
        }

        // Now loop back around and cross the finish gate from South (lat 37.7748) to North (lat 37.7750)
        time += 1_000_000_000L
        val approachPoint = GpsPoint(37.7748, -122.4194, 10.0, 60.0, 0f, 2.0f, time)
        engine.processFrame(approachPoint, 60.0, 0f, time)

        time += 500_000_000L
        val crossPoint = GpsPoint(37.7750, -122.4194, 10.0, 60.0, 0f, 2.0f, time)
        val stateAfterCross = engine.processFrame(crossPoint, 60.0, 0f, time)

        assertEquals("Lap count should increment to 2", 2, stateAfterCross.currentLap)
        assertNotNull("Last lap time should be recorded", stateAfterCross.lastLapTimeSec)
        assertTrue("Last lap time should be > 20 seconds", stateAfterCross.lastLapTimeSec!! > 20.0)
    }

    @Test
    fun testCornerDetection_apexMinSpeedRecorded() {
        var time = 1_000_000_000L

        // Straight line before corner
        val stateStraight = engine.processFrame(null, 65.0, 0.05f, time)
        assertNull("Should not be in corner", stateStraight.currentCornerApexMph)

        // Enter corner: lateral G exceeds 0.40G
        time += 200_000_000L
        val stateEntry = engine.processFrame(null, 60.0, 0.85f, time)
        assertTrue("Should be detected inside corner", stateEntry.isInCorner)

        // Mid-corner apex (speed slows down to 42 mph, G peaks at 1.15G)
        time += 400_000_000L
        val stateApex = engine.processFrame(null, 42.0, 1.15f, time)
        assertEquals(42.0, stateApex.currentCornerApexMph!!, 0.5)

        // Exit corner: lateral G drops below 0.22G, speed recovers to 58 mph
        time += 400_000_000L
        val stateExit = engine.processFrame(null, 58.0, 0.15f, time)
        assertTrue("Corner event should be recorded", stateExit.lastCornerSummary != null)
        assertEquals(42.0, stateExit.lastCornerSummary!!.apexMinSpeedMph, 0.5)
        assertEquals(1.15f, stateExit.lastCornerSummary!!.peakLateralG, 0.05f)
    }
}