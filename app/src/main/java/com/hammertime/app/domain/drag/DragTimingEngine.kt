package com.hammertime.app.domain.drag

import com.hammertime.app.domain.model.DragRunStatus
import com.hammertime.app.domain.model.DragSplitResult
import com.hammertime.app.domain.model.DragState
import kotlin.math.max

class DragTimingEngine {
    private var status = DragRunStatus.WAITING_FOR_STOP
    private var launchTimestampNanos: Long = 0L
    private var lastTimestampNanos: Long = 0L
    private var lastSpeedMph: Double = 0.0
    private var cumulativeDistanceMeters: Double = 0.0
    private var peakG: Float = 0f

    private val splits = mutableMapOf<String, DragSplitResult>()
    private var splitsSnapshot: Map<String, DragSplitResult> = emptyMap()

    private companion object {
        const val FALSE_START_GRACE_SEC = 2.0
        const val FALSE_START_SPEED_MPH = 2.0
        const val MIN_DISTANCE_FOR_REARM_METERS = 50.0
    }

    fun processFrame(
        speedMph: Double,
        longitudinalG: Float,
        timestampNanos: Long
    ): DragState {
        peakG = max(peakG, longitudinalG)

        when (status) {
            DragRunStatus.WAITING_FOR_STOP -> {
                if (speedMph < 0.5) {
                    status = DragRunStatus.ARMED_READY_FOR_LAUNCH
                    resetRun()
                }
            }

            DragRunStatus.ARMED_READY_FOR_LAUNCH -> {
                if (speedMph > 0.6 || longitudinalG > 0.30f) {
                    status = DragRunStatus.LAUNCHED
                    launchTimestampNanos = timestampNanos
                    lastTimestampNanos = timestampNanos
                    lastSpeedMph = speedMph
                    cumulativeDistanceMeters = 0.0
                    peakG = longitudinalG
                }
            }

            DragRunStatus.LAUNCHED -> {
                val dtSec = (timestampNanos - lastTimestampNanos) / 1_000_000_000.0
                if (dtSec > 0.0) {
                    val avgSpeedMps = ((lastSpeedMph + speedMph) / 2.0) / 2.23693629
                    val distStep = avgSpeedMps * dtSec
                    cumulativeDistanceMeters += distStep

                    // Check speed splits
                    checkSpeedTarget(lastSpeedMph, speedMph, lastTimestampNanos, timestampNanos, 30.0, "0-30")
                    checkSpeedTarget(lastSpeedMph, speedMph, lastTimestampNanos, timestampNanos, 60.0, "0-60")
                    checkSpeedTarget(lastSpeedMph, speedMph, lastTimestampNanos, timestampNanos, 100.0, "0-100")

                    // Check distance splits
                    checkDistanceTarget(cumulativeDistanceMeters - distStep, cumulativeDistanceMeters, lastTimestampNanos, timestampNanos, lastSpeedMph, speedMph, 201.168, "1/8 Mile")
                    checkDistanceTarget(cumulativeDistanceMeters - distStep, cumulativeDistanceMeters, lastTimestampNanos, timestampNanos, lastSpeedMph, speedMph, 402.336, "1/4 Mile")

                    lastTimestampNanos = timestampNanos
                    lastSpeedMph = speedMph
                }

                if (splits.containsKey("1/4 Mile")) {
                    status = DragRunStatus.FINISHED
                } else if (speedMph < FALSE_START_SPEED_MPH) {
                    val elapsedSec = (timestampNanos - launchTimestampNanos) / 1_000_000_000.0
                    if (cumulativeDistanceMeters > MIN_DISTANCE_FOR_REARM_METERS) {
                        // Run was abandoned after moving: wait for the car to stop again.
                        status = DragRunStatus.WAITING_FOR_STOP
                    } else if (elapsedSec > FALSE_START_GRACE_SEC) {
                        // Launch was triggered by a bump/G spike but the car never got going.
                        status = DragRunStatus.WAITING_FOR_STOP
                    }
                }
            }

            DragRunStatus.FINISHED -> {
                if (speedMph < 0.5) {
                    status = DragRunStatus.ARMED_READY_FOR_LAUNCH
                    resetRun()
                }
            }
        }

        val activeSeconds = if (status == DragRunStatus.LAUNCHED && launchTimestampNanos > 0L) {
            (timestampNanos - launchTimestampNanos) / 1_000_000_000.0
        } else 0.0

        return DragState(
            status = status,
            currentSpeedMph = speedMph,
            peakAccelG = peakG,
            activeRunSeconds = activeSeconds,
            splits = splitsSnapshot
        )
    }

    private fun checkSpeedTarget(
        v1: Double,
        v2: Double,
        t1: Long,
        t2: Long,
        targetMph: Double,
        label: String
    ) {
        if (splits.containsKey(label)) return
        if (v1 < targetMph && v2 >= targetMph && v2 > v1) {
            val ratio = (targetMph - v1) / (v2 - v1)
            val crossingNanos = t1 + (ratio * (t2 - t1)).toLong()
            val elapsedSec = (crossingNanos - launchTimestampNanos) / 1_000_000_000.0
            splits[label] = DragSplitResult(
                label = label,
                elapsedSeconds = elapsedSec,
                trapSpeedMph = targetMph,
                isCompleted = true
            )
            splitsSnapshot = splits.toMap()
        }
    }

    private fun checkDistanceTarget(
        d1: Double,
        d2: Double,
        t1: Long,
        t2: Long,
        v1: Double,
        v2: Double,
        targetMeters: Double,
        label: String
    ) {
        if (splits.containsKey(label)) return
        if (d1 < targetMeters && d2 >= targetMeters && d2 > d1) {
            val ratio = (targetMeters - d1) / (d2 - d1)
            val crossingNanos = t1 + (ratio * (t2 - t1)).toLong()
            val elapsedSec = (crossingNanos - launchTimestampNanos) / 1_000_000_000.0
            val trapSpeed = v1 + ratio * (v2 - v1)
            splits[label] = DragSplitResult(
                label = label,
                elapsedSeconds = elapsedSec,
                trapSpeedMph = trapSpeed,
                isCompleted = true
            )
            splitsSnapshot = splits.toMap()
        }
    }

    fun manualReset() {
        status = DragRunStatus.WAITING_FOR_STOP
        resetRun()
    }

    private fun resetRun() {
        splits.clear()
        splitsSnapshot = emptyMap()
        cumulativeDistanceMeters = 0.0
        launchTimestampNanos = 0L
        lastTimestampNanos = 0L
        lastSpeedMph = 0.0
        peakG = 0f
    }
}
