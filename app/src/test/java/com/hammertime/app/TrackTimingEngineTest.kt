package com.hammertime.app

import com.hammertime.app.domain.fusion.SpeedSensorFusion
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.track.TrackTimingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

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

    // --- Closed-loop simulation helpers -------------------------------------------------------

    private val lat0 = 37.7749
    private val lon0 = -122.4194

    // Waypoints as (north, east) metres from the start/finish line. 810 m loop, driven north
    // across the line. The line sits mid-segment of the last leg so samples never land on it.
    private val loop = listOf(
        0.0 to 0.0, 200.0 to 0.0, 200.0 to 100.0, -105.0 to 100.0, -105.0 to 0.0, 0.0 to 0.0
    )
    private val loopLength = loop.zipWithNext { a, b -> hypot(b.first - a.first, b.second - a.second) }.sum()

    private fun pointAt(arc: Double): Pair<Double, Double> {
        var remaining = arc % loopLength
        for ((a, b) in loop.zipWithNext()) {
            val len = hypot(b.first - a.first, b.second - a.second)
            if (remaining <= len) {
                val f = remaining / len
                return (a.first + f * (b.first - a.first)) to (a.second + f * (b.second - a.second))
            }
            remaining -= len
        }
        return loop.last()
    }

    private fun fix(north: Double, east: Double, speedMps: Double, nanos: Long, bearing: Float = 0f) = GpsPoint(
        latitude = lat0 + north / 111_139.0,
        longitude = lon0 + east / (111_139.0 * cos(Math.toRadians(lat0))),
        altitudeMeters = 10.0,
        speedMph = speedMps * 2.23693629,
        bearingDegrees = bearing,
        accuracyMeters = 2f,
        timestampNanos = nanos
    )

    private fun stationary() = GpsPoint(lat0, lon0, 10.0, 0.0, 0f, 2f, 1_000_000_000L, hasBearing = false)

    @Test
    fun testSetGate_rejectedWithoutBearing() {
        assertFalse(engine.setFinishGateAtCurrentLocation(stationary()))
        val slow = fix(0.0, 0.0, 0.2, 1_000_000_000L) // hasBearing but crawling
        assertFalse(engine.setFinishGateAtCurrentLocation(slow))
        assertFalse(engine.processFrame(null, 0.0, 0f, 2_000_000_000L).finishGateSet)

        assertTrue(engine.setFinishGateAtCurrentLocation(fix(0.0, 0.0, 15.0, 1_000_000_000L)))
        assertTrue(engine.processFrame(null, 33.0, 0f, 2_000_000_000L).finishGateSet)
    }

    @Test
    fun testLapTimesAndInterpolatedDelta_overClosedLoop() {
        val startNanos = 1_000_000_000L
        assertTrue(engine.setFinishGateAtCurrentLocation(fix(0.0, 0.0, 12.0, startNanos)))

        var arc = 0.0
        var speed = 12.0 // m/s on lap 1, 13 m/s on lap 2
        var latest: GpsPoint? = null
        var delta: Float? = null
        var state = engine.processFrame(null, speed * 2.23693629, 0f, startNanos)
        var step = 0

        while (state.currentLap < 3 && step < 3000) {
            step++
            val nanos = startNanos + step * 100_000_000L
            arc += speed * 0.1
            if (step % 10 == 0) { // 1 Hz GPS fix
                val (n, e) = pointAt(arc)
                latest = fix(n, e, speed, nanos)
            }
            state = engine.processFrame(latest, speed * 2.23693629, 0f, nanos)
            if (state.currentLap == 2) {
                speed = 13.0
                if (delta == null && state.currentLapTimeSec >= 30.0) delta = state.deltaSec
            }
            if (state.currentLap == 1) assertNull("No reference lap yet", state.deltaSec)
        }

        assertEquals(3, state.currentLap)
        assertEquals(2, state.lapHistory.size)
        assertEquals(810.0 / 12.0, state.lapHistory[0].lapTimeSec, 0.1)
        assertTrue(state.lapHistory[0].isBest)
        assertEquals(810.0 / 13.0, state.lapHistory[1].lapTimeSec, 0.7)
        assertTrue("Faster lap becomes the best lap", state.lapHistory[1].isBest)
        assertEquals(state.lapHistory[1].lapTimeSec, state.bestLapTimeSec!!, 1e-9)

        // 30 s into lap 2 the car is ~390 m round; lap 1 took ~32.5 s to get there => about -2.5 s.
        assertNotNull("Delta should be available against the reference lap", delta)
        assertTrue("Delta $delta should be ~-2.5s", delta!! in -3.2f..-1.8f)
    }

    @Test
    fun testGate_ignoresCrossingAgainstDirectionOfTravel() {
        val t0 = 1_000_000_000L
        assertTrue(engine.setFinishGateAtCurrentLocation(fix(0.0, 0.0, 9.0, t0)))

        // Out north past 50 m, then back south across the line, then north across it again.
        val norths = listOf(30.0, 60.0, 90.0, 45.0, -15.0, 15.0)
        var state = engine.processFrame(null, 20.0, 0f, t0)
        val laps = mutableListOf<Int>()
        norths.forEachIndexed { i, n ->
            val t = t0 + (i + 1) * 1_000_000_000L
            state = engine.processFrame(fix(n, 0.0, 9.0, t), 20.0, 0f, t)
            laps.add(state.currentLap)
        }

        // Southbound crossing (index 4) must not count; northbound (index 5) must.
        assertEquals(listOf(1, 1, 1, 1, 1, 2), laps)
    }

    @Test
    fun testReset_clearsGate_andNoBogusLapAfterwards() {
        val t0 = 1_000_000_000L
        engine.setFinishGateAtCurrentLocation(fix(0.0, 0.0, 9.0, t0))
        engine.processFrame(fix(100.0, 0.0, 9.0, t0 + 1_000_000_000L), 20.0, 0f, t0 + 1_000_000_000L)

        engine.reset()
        assertFalse(engine.processFrame(null, 0.0, 0f, t0 + 2_000_000_000L).finishGateSet)

        // Driving over the old gate position after reset must not record a lap.
        var state = engine.processFrame(fix(-50.0, 0.0, 9.0, t0 + 3_000_000_000L), 20.0, 0f, t0 + 3_000_000_000L)
        state = engine.processFrame(fix(60.0, 0.0, 9.0, t0 + 10_000_000_000L), 20.0, 0f, t0 + 10_000_000_000L)
        assertEquals(1, state.currentLap)
        assertTrue(state.lapHistory.isEmpty())
    }
}
