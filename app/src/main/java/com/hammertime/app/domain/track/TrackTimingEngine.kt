package com.hammertime.app.domain.track

import com.hammertime.app.domain.model.CornerEvent
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.model.LapRecord
import com.hammertime.app.domain.model.TrackState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class FinishGate(
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double
)

data class LapPoint(
    val distanceMeters: Double,
    val elapsedSeconds: Double,
    val speedMph: Double
)

class TrackTimingEngine {
    private var finishGate: FinishGate? = null
    private var previousPoint: GpsPoint? = null

    private var currentLap = 1
    private var lapStartTimeNanos: Long = 0L
    private var lapDistanceMeters: Double = 0.0

    private var lastLapTimeSec: Double? = null
    private var bestLapTimeSec: Double? = null

    // Session lap history
    private val lapHistory = mutableListOf<LapRecord>()

    // Current lap peaks
    private var currentLapTopSpeed = 0.0
    private var currentLapPeakLatG = 0f

    // Reference trajectory for predictive delta
    private var bestLapTrajectory = mutableListOf<LapPoint>()
    private var currentLapTrajectory = mutableListOf<LapPoint>()

    // Corner detection state
    private var inCorner = false
    private var cornerEntrySpeed = 0.0
    private var minCornerSpeed = Double.MAX_VALUE
    private var peakLatG = 0f
    private var lastCornerSummary: CornerEvent? = null

    fun setFinishGateAtCurrentLocation(current: GpsPoint) {
        val gateHalfWidthMeters = 15.0
        val headingRad = Math.toRadians(current.bearingDegrees.toDouble())
        val perpAngle = headingRad + Math.PI / 2.0

        val metersPerLatDegree = 111_139.0
        val metersPerLonDegree = 111_139.0 * cos(Math.toRadians(current.latitude))

        val dLat = (gateHalfWidthMeters * cos(perpAngle)) / metersPerLatDegree
        val dLon = (gateHalfWidthMeters * sin(perpAngle)) / metersPerLonDegree

        finishGate = FinishGate(
            lat1 = current.latitude - dLat,
            lon1 = current.longitude - dLon,
            lat2 = current.latitude + dLat,
            lon2 = current.longitude + dLon
        )

        lapStartTimeNanos = current.timestampNanos
        lapDistanceMeters = 0.0
        currentLapTrajectory.clear()
        currentLapTopSpeed = current.speedMph
        currentLapPeakLatG = 0f
    }

    fun processFrame(
        gpsPoint: GpsPoint?,
        speedMph: Double,
        lateralG: Float,
        timestampNanos: Long
    ): TrackState {
        // Track current lap peaks
        currentLapTopSpeed = max(currentLapTopSpeed, speedMph)
        currentLapPeakLatG = max(currentLapPeakLatG, abs(lateralG))

        // 1. Process Corner Speed & Apex
        processCornering(speedMph, lateralG)

        val prev = previousPoint
        if (gpsPoint != null) {
            previousPoint = gpsPoint

            if (prev != null) {
                val dtSec = (gpsPoint.timestampNanos - prev.timestampNanos) / 1_000_000_000.0
                if (dtSec > 0.0) {
                    val avgSpeedMps = ((prev.speedMph + speedMph) / 2.0) / 2.23693629
                    lapDistanceMeters += avgSpeedMps * dtSec

                    if (lapStartTimeNanos > 0L) {
                        val currentElapsed = (timestampNanos - lapStartTimeNanos) / 1_000_000_000.0
                        val lastLoggedDist = currentLapTrajectory.lastOrNull()?.distanceMeters ?: -10.0
                        if (lapDistanceMeters - lastLoggedDist >= 5.0) {
                            currentLapTrajectory.add(
                                LapPoint(
                                    distanceMeters = lapDistanceMeters,
                                    elapsedSeconds = currentElapsed,
                                    speedMph = speedMph
                                )
                            )
                        }
                    }
                }

                // 2. Check Finish Gate Crossing
                val gate = finishGate
                if (gate != null && lapDistanceMeters > 50.0) {
                    val intersection = checkSegmentIntersection(
                        prev.latitude, prev.longitude,
                        gpsPoint.latitude, gpsPoint.longitude,
                        gate.lat1, gate.lon1,
                        gate.lat2, gate.lon2
                    )

                    if (intersection.first) {
                        val fraction = intersection.second
                        val crossingTimeNanos = prev.timestampNanos + (fraction * (gpsPoint.timestampNanos - prev.timestampNanos)).toLong()
                        val lapTimeSec = (crossingTimeNanos - lapStartTimeNanos) / 1_000_000_000.0

                        val isBest = bestLapTimeSec == null || lapTimeSec < bestLapTimeSec!!
                        lastLapTimeSec = lapTimeSec
                        if (isBest) {
                            bestLapTimeSec = lapTimeSec
                            bestLapTrajectory = ArrayList(currentLapTrajectory)
                        }

                        // Record lap in history
                        lapHistory.add(
                            LapRecord(
                                lapNumber = currentLap,
                                lapTimeSec = lapTimeSec,
                                isBest = isBest,
                                topSpeedMph = currentLapTopSpeed,
                                peakLatG = currentLapPeakLatG
                            )
                        )

                        currentLap++
                        lapStartTimeNanos = crossingTimeNanos
                        lapDistanceMeters = 0.0
                        currentLapTrajectory.clear()
                        currentLapTopSpeed = speedMph
                        currentLapPeakLatG = abs(lateralG)
                    }
                }
            }
        }

        // 3. Compute Real-time Delta (+/- time)
        val currentLapElapsed = if (lapStartTimeNanos > 0L) {
            (timestampNanos - lapStartTimeNanos) / 1_000_000_000.0
        } else 0.0

        val deltaSec = computeDelta(lapDistanceMeters, currentLapElapsed)

        return TrackState(
            currentLap = currentLap,
            currentLapTimeSec = currentLapElapsed,
            lastLapTimeSec = lastLapTimeSec,
            bestLapTimeSec = bestLapTimeSec,
            deltaSec = deltaSec,
            isInCorner = inCorner,
            currentCornerApexMph = if (inCorner && minCornerSpeed < 200.0) minCornerSpeed else null,
            lastCornerSummary = lastCornerSummary,
            finishGateSet = finishGate != null,
            lapHistory = lapHistory.toList(),
            currentLapTopSpeedMph = currentLapTopSpeed,
            currentLapPeakLatG = currentLapPeakLatG,
            currentLapDistanceMeters = lapDistanceMeters
        )
    }

    private fun processCornering(speedMph: Double, lateralG: Float) {
        val absG = abs(lateralG)
        val CORNER_ENTRY_THRESHOLD = 0.40f
        val CORNER_EXIT_THRESHOLD = 0.22f

        if (!inCorner && absG >= CORNER_ENTRY_THRESHOLD && speedMph > 15.0) {
            inCorner = true
            cornerEntrySpeed = speedMph
            minCornerSpeed = speedMph
            peakLatG = absG
        } else if (inCorner) {
            if (absG > peakLatG) peakLatG = absG
            if (speedMph < minCornerSpeed) minCornerSpeed = speedMph

            if (absG <= CORNER_EXIT_THRESHOLD) {
                inCorner = false
                lastCornerSummary = CornerEvent(
                    entrySpeedMph = cornerEntrySpeed,
                    apexMinSpeedMph = minCornerSpeed,
                    exitSpeedMph = speedMph,
                    peakLateralG = peakLatG
                )
            }
        }
    }

    private fun computeDelta(currentDist: Double, currentElapsedSec: Double): Float? {
        if (bestLapTrajectory.isEmpty() || currentDist < 10.0) return null

        val refPoint = bestLapTrajectory.minByOrNull { abs(it.distanceMeters - currentDist) }
        return if (refPoint != null && abs(refPoint.distanceMeters - currentDist) < 25.0) {
            (currentElapsedSec - refPoint.elapsedSeconds).toFloat()
        } else null
    }

    private fun checkSegmentIntersection(
        p1Lat: Double, p1Lon: Double,
        p2Lat: Double, p2Lon: Double,
        g1Lat: Double, g1Lon: Double,
        g2Lat: Double, g2Lon: Double
    ): Pair<Boolean, Double> {
        val dLat = p2Lat - p1Lat
        val dLon = p2Lon - p1Lon
        val gateLat = g2Lat - g1Lat
        val gateLon = g2Lon - g1Lon

        val det = -gateLon * dLat + dLon * gateLat
        if (abs(det) < 1e-12) return Pair(false, 0.0)

        val t = (-gateLon * (g1Lat - p1Lat) + gateLat * (g1Lon - p1Lon)) / det
        val s = ( dLat * (g1Lon - p1Lon) - dLon * (g1Lat - p1Lat)) / det

        val intersects = (s in 0.0..1.0) && (t in 0.0..1.0)
        return Pair(intersects, t)
    }

    fun reset() {
        lapStartTimeNanos = 0L
        lapDistanceMeters = 0.0
        currentLap = 1
        lastLapTimeSec = null
        bestLapTimeSec = null
        lapHistory.clear()
        bestLapTrajectory.clear()
        currentLapTrajectory.clear()
        inCorner = false
        lastCornerSummary = null
        currentLapTopSpeed = 0.0
        currentLapPeakLatG = 0f
    }
}