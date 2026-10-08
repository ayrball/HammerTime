package com.hammertime.app.data.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CameraVideoRecorder(private val context: Context) {
    private companion object {
        const val TAG = "CameraVideoRecorder"
        const val SIDECAR_FLUSH_INTERVAL_NANOS = 1_000_000_000L
    }

    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var activeRecording: Recording? = null

    // The recorder gets its own thread; telemetry I/O must never queue behind (or in front of) video.
    private val videoExecutor = Executors.newSingleThreadExecutor()
    private val sidecarExecutor = Executors.newSingleThreadExecutor()

    // sidecarWriter and lastFlushNanos are only touched on sidecarExecutor.
    private var sidecarWriter: BufferedWriter? = null
    private var lastFlushNanos = 0L
    @Volatile private var sidecarOpen = false

    /**
     * Binds preview + video capture, or if already bound just re-targets the preview at
     * [previewView]. Rebinding would call unbindAll(), which aborts an in-flight recording; the
     * preview view is recreated whenever the UI switches between camera HUD and dashboard.
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val existingPreview = preview
            if (existingPreview != null && videoCapture != null) {
                existingPreview.surfaceProvider = previewView.surfaceProvider
                onReady()
                return@addListener
            }

            val cameraProvider = cameraProviderFuture.get()

            val newPreview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val qualitySelector = QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setExecutor(videoExecutor)
                .build()

            val newVideoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    newPreview,
                    newVideoCapture
                )
                preview = newPreview
                videoCapture = newVideoCapture
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Starts a recording. [onEvent] is invoked on the main thread for every [VideoRecordEvent];
     * a [VideoRecordEvent.Finalize] means recording has ended, whether stopped or failed.
     *
     * @return the video file, or null if the camera isn't bound or a recording is already active.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        outputDir: File,
        onEvent: (VideoRecordEvent) -> Unit
    ): File? {
        val capture = videoCapture ?: return null
        if (activeRecording != null) return null

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(outputDir, "HAMMERTIME_$timeStamp.mp4")
        val sidecarFile = File(outputDir, "HAMMERTIME_$timeStamp.telemetry.jsonl")

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val pending = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(videoFile).build())
            .also { if (audioGranted) it.withAudioEnabled() }

        openSidecar(sidecarFile)

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    // Same clock as SensorEvent.timestamp, so telemetry can be aligned to the video.
                    writeSidecarLine("{\"type\":\"video_start\",\"t\":${SystemClock.elapsedRealtimeNanos()}}")
                }
                is VideoRecordEvent.Finalize -> {
                    closeSidecar()
                    activeRecording = null
                    if (event.hasError()) {
                        Log.w(TAG, "Recording finalized with error ${event.error}", event.cause)
                    }
                }
                else -> Unit
            }
            onEvent(event)
        }

        return videoFile
    }

    /** Queues one JSON line for the sidecar file; a no-op unless a recording is active. */
    fun logTelemetryPoint(jsonLine: String) {
        if (!sidecarOpen) return
        writeSidecarLine(jsonLine)
    }

    /**
     * Requests the recording to stop. The recording (and [isRecordingActive]) stays live until the
     * Finalize event arrives, so a new recording can't start on top of a half-written file.
     */
    fun stopRecording() {
        activeRecording?.stop()
        closeSidecar()
    }

    val isRecordingActive: Boolean get() = activeRecording != null

    private fun openSidecar(file: File) {
        sidecarOpen = true
        sidecarExecutor.execute {
            try {
                sidecarWriter = BufferedWriter(FileWriter(file, true), 32 * 1024)
                lastFlushNanos = SystemClock.elapsedRealtimeNanos()
            } catch (e: Exception) {
                Log.e(TAG, "Could not open telemetry sidecar", e)
                sidecarWriter = null
            }
        }
    }

    private fun writeSidecarLine(line: String) {
        sidecarExecutor.execute {
            val writer = sidecarWriter ?: return@execute
            try {
                writer.write(line)
                writer.write('\n'.code)
                val now = SystemClock.elapsedRealtimeNanos()
                if (now - lastFlushNanos >= SIDECAR_FLUSH_INTERVAL_NANOS) {
                    writer.flush()
                    lastFlushNanos = now
                }
            } catch (e: Exception) {
                Log.w(TAG, "Telemetry sidecar write failed", e)
            }
        }
    }

    private fun closeSidecar() {
        sidecarOpen = false
        sidecarExecutor.execute {
            try {
                sidecarWriter?.flush()
                sidecarWriter?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Telemetry sidecar close failed", e)
            }
            sidecarWriter = null
        }
    }
}
