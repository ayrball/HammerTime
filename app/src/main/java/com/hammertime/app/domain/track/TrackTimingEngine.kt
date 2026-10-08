package com.hammertime.app.domain.track

import com.hammertime.app.domain.model.CornerEvent
import com.hammertime.app.domain.model.GpsPoint
import com.hammertime.app.domain.model.LapRecord
import com.hammertime.app.domain.model.TrackState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class FinishGate(
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double,
    /** Direction of travel (radians clockwise from north) the gate was set with. */
    val headingRad: Double = 0.0
)

data class LapPoint(
    val distanceMeters: Double,
    val elapsedSeconds: Double,
    val speedMph: Double
)

class TrackTimingEngine {
    private companion object {
        const val METERS_PER_LAT_DEGREE = 111_139.0
        const val MPH_PER_MPS = 2.23693629
        const val GATE_HALF_WIDTH_METERS = 15.0
        const val MIN_GATE_SET_SPEED_MPH = 3.0
        const val MIN_LAP_DISTANCE_METERS = 50.0
        const val TRAJECTORY_SPACING_METERS = 5.0
        const val MAX_EXTRAPOLATION_SEC = 2.0
    }

    private var finishGate: FinishGate? = null
    private var previousPoint: GpsPoint? = null

    private var currentLap = 1
    private var lapStartTimeNanos: Long = 0L
    private var lapDistanceMeters: Double = 0.0
    private var lastFixTimeNanos: Long = 0L

    private var lastLapTimeSec: Double? = null
    private var bestLapTimeSec: Double? = null

    // Session lap history (snapshot is rebuilt only when a lap is added)
    private val lapHistory = mutableListOf<LapRecord>()
    private var lapHistorySnapshot: List<LapRecord> = emptyList()

    // Current lap peaks
    private var currentLapTopSpeed = 0.0
    private var currentLapPeakLatG = 0f

    // Reference trajectory for predictive delta (ordered by increasing distance)
    private var bestLapTrajectory = mutableListOf<LapPoint>()
    private var currentLapTrajectory = mutableListOf<LapPoint>()

    // Corner detection state
    private var inCorner = false
    private var cornerEntrySpeed = 0.0
    private var minCornerSpeed = Double.MAX_VALUE
    private var peakLatG = 0f
    private var lastCornerSummary: CornerEvent? = null

    /**
     * Places the finish gate across the direction of travel at [current].
     *
     * @return false (and leaves any existing gate untouched) if the GPS fix has no usable bearing,
     *   which happens when the vehicle is stationary: the gate would otherwise face north.
     */
    fun setFinishGateAtCurrentLocation(current: GpsPoint): Boolean {
        if (!current.hasBearing || current.speedMph < MIN_GATE_SET_SPEED_MPH) return false

        val headingRad = Math.toRadians(current.bearingDegrees.toDouble())
        val perpAngle = headingRad + Math.PI / 2.0

        val metersPerLonDegree = METERS_PER_LAT_DEGREE * cos(Math.toRadians(current.latitude))

        val dLat = (GATE_HALF_WIDTH_METERS * cos(perpAngle)) / METERS_PER_LAT_DEGREE
        val dLon = (GATE_HALF_WIDTH_METERS * sin(perpAngle)) / metersPerLonDegree

        finishGate = FinishGate(
            lat1 = current.latitude - dLat,
            lon1 = current.longitude - dLon,
            lat2 = current.latitude + dLat,
            lon2 = current.longitude + dLon,
            headingRad = headingRad
        )

        previousPoint = current
        lastFixTimeNanos = current.timestampNanos
        startNewLap(current.timestampNanos, carriedDistance = 0.0, speedMph = current.speedMph, latG = 0f)
        return true
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
        if (gpsPoint != null && gpsPoint !== prev) {
            previousPoint = gpsPoint

            if (prev != null && gpsPoint.timestampNanos > prev.timestampNanos) {
                val segmentMeters = distanceMeters(prev, gpsPoint)
                lapDistanceMeters += segmentMeters
                lastFixTimeNanos = gpsPoint.timestampNanos

                if (lapStartTimeNanos > 0L) {
                    val fixElapsed = (gpsPoint.timestampNanos - lapStartTimeNanos) / 1_000_000_000.0
                    val lastLoggedDist = currentLapTrajectory.lastOrNull()?.distanceMeters ?: -TRAJECTORY_SPACING_METERS
                    if (lapDistanceMeters - lastLoggedDist >= TRAJECTORY_SPACING_METERS) {
                        currentLapTrajectory.add(
                            LapPoint(
                                distanceMeters = lapDistanceMeters,
                                elapsedSeconds = fixElapsed,
                                speedMph = gpsPoint.speedMph
                            )
                        )
                    }
                }

                // 2. Check Finish Gate Crossing
                val gate = finishGate
                if (gate != null && lapStartTimeNanos > 0L && lapDistanceMeters > MIN_LAP_DISTANCE_METERS &&
                    movesWithGateHeading(prev, gpsPoint, gate)
                ) {
                    val intersection = checkSegmentIntersection(
                        prev.latitude, prev.longitude,
                        gpsPoint.latitude, gpsPoint.longitude,
                        gate.lat1, gate.lon1,
                        gate.lat2, gate.lon2
                    )

                    if (intersection.first) {
                        val fraction = intersection.second
                        val crossingTimeNanos = prev.timestampNanos +
                            (fraction * (gpsPoint.timestampNanos - prev.timestampNanos)).toLong()
                        val lapTimeSec = (crossingTimeNanos - lapStartTimeNanos) / 1_000_000_000.0
                        val metersAfterCrossing = (1.0 - fraction) * segmentMeters
                        val lapDistanceAtCrossing = lapDistanceMeters - metersAfterCrossing

                        val isBest = bestLapTimeSec == null || lapTimeSec < bestLapTimeSec!!
                        lastLapTimeSec = lapTimeSec
                        if (isBest) {
                            bestLapTimeSec = lapTimeSec
                            // Close the reference trajectory exactly at the line so the delta stays
                            // available right up to the next crossing.
                            currentLapTrajectory.add(
                                LapPoint(lapDistanceAtCrossing, lapTimeSec, gpsPoint.speedMph)
                            )
                            bestLapTrajectory = ArrayList(currentLapTrajectory)
                        }

                        lapHistory.add(
                            LapRecord(
                                lapNumber = currentLap,
                                lapTimeSec = lapTimeSec,
                                isBest = isBest,
                                topSpeedMph = currentLapTopSpeed,
                                peakLatG = currentLapPeakLatG
                            )
                        )
                        lapHistorySnapshot = lapHistory.toList()

                        currentLap++
                        startNewLap(crossingTimeNanos, metersAfterCrossing, speedMph, lateralG)
                    }
                }
            }
        }

        // 3. Compute Real-time Delta (+/- time)
        val currentLapElapsed = if (lapStartTimeNanos > 0L) {
            (timestampNanos - lapStartTimeNanos) / 1_000_000_000.0
        } else 0.0

        val liveDistance = liveLapDistance(speedMph, timestampNanos)
        val deltaSec = if (lapStartTimeNanos > 0L) computeDelta(liveDistance, currentLapElapsed) else null

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
            lapHistory = lapHistorySnapshot,
            currentLapTopSpeedMph = currentLapTopSpeed,
            currentLapPeakLatG = currentLapPeakLatG,
            currentLapDistanceMeters = liveDistance
        )
    }

    private fun startNewLap(startNanos: Long, carriedDistance: Double, speedMph: Double, latG: Float) {
        lapStartTimeNanos = startNanos
        lapDistanceMeters = carriedDistance
        currentLapTrajectory.clear()
        currentLapTrajectory.add(LapPoint(0.0, 0.0, speedMph))
        currentLapTopSpeed = speedMph
        currentLapPeakLatG = abs(latG)
    }

    /**
     * Lap distance only advances on GPS fixes (~1 Hz), but the lap clock and delta update at IMU
     * rate. Dead-reckon forward from the last fix so the delta doesn't saw-tooth between fixes.
     */
    private fun liveLapDistance(speedMph: Double, nowNanos: Long): Double {
        if (lastFixTimeNanos <= 0L) return lapDistanceMeters
        val sinceFixSec = ((nowNanos - lastFixTimeNanos) / 1_000_000_000.0)
            .coerceIn(0.0, MAX_EXTRAPOLATION_SEC)
        return lapDistanceMeters + sinceFixSec * speedMph / MPH_PER_MPS
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

    /** Time difference vs. the best lap at the same distance, linearly interpolated. */
    private fun computeDelta(currentDist: Double, currentElapsedSec: Double): Float? {
        val ref = bestLapTrajectory
        if (ref.size < 2 || currentDist < 10.0) return null
        if (currentDist < ref.first().distanceMeters || currentDist > ref.last().distanceMeters) return null

        // First reference point at or beyond currentDist.
        var lo = 0
        var hi = ref.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ref[mid].distanceMeters < currentDist) lo = mid + 1 else hi = mid
        }

        val after = ref[lo]
        val refElapsed = if (lo == 0) {
            after.elapsedSeconds
        } else {
            val before = ref[lo - 1]
            val span = after.distanceMeters - before.distanceMeters
            if (span <= 1e-6) after.elapsedSeconds
            else before.elapsedSeconds +
                (currentDist - before.distanceMeters) / span * (after.elapsedSeconds - before.elapsedSeconds)
        }
        return (currentElapsedSec - refElapsed).toFloat()
    }

    private fun distanceMeters(a: GpsPoint, b: GpsPoint): Double {
        val meanLatRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val dNorth = (b.latitude - a.latitude) * METERS_PER_LAT_DEGREE
        val dEast = (b.longitude - a.longitude) * METERS_PER_LAT_DEGREE * cos(meanLatRad)
        return sqrt(dNorth * dNorth + dEast * dEast)
    }

    /** Only count crossings made in the direction the gate was set with (not reversing over it). */
    private fun movesWithGateHeading(from: GpsPoint, to: GpsPoint, gate: FinishGate): Boolean {
        val meanLatRad = Math.toRadians((from.latitude + to.latitude) / 2.0)
        val dNorth = (to.latitude - from.latitude) * METERS_PER_LAT_DEGREE
        val dEast = (to.longitude - from.longitude) * METERS_PER_LAT_DEGREE * cos(meanLatRad)
        return dEast * sin(gate.headingRad) + dNorth * cos(gate.headingRad) > 0.0
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

    /** Clears all session data including the gate; the gate must be set again to resume timing. */
    fun reset() {
        finishGate = null
        previousPoint = null
        lapStartTimeNanos = 0L
        lapDistanceMeters = 0.0
        lastFixTimeNanos = 0L
        currentLap = 1
        lastLapTimeSec = null
        bestLapTimeSec = null
        lapHistory.clear()
        lapHistorySnapshot = emptyList()
        bestLapTrajectory.clear()
        currentLapTrajectory.clear()
        inCorner = false
        lastCornerSummary = null
        currentLapTopSpeed = 0.0
        currentLapPeakLatG = 0f
    }
}
