package com.hammertime.app.data.camera

import android.annotation.SuppressLint
import android.content.Context
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
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CameraVideoRecorder(private val context: Context) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var sidecarFileWriter: FileWriter? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val qualitySelector = QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setExecutor(backgroundExecutor)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                onReady()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

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

        try {
            sidecarFileWriter = FileWriter(sidecarFile, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = capture.output
            .prepareRecording(context, fileOutputOptions)
            .apply {
                // Enable audio if permission is available
                try {
                    withAudioEnabled()
                } catch (ignored: SecurityException) {}
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    closeSidecar()
                    activeRecording = null
                }
                onEvent(event)
            }

        return videoFile
    }

    fun logTelemetryPoint(jsonLine: String) {
        backgroundExecutor.execute {
            try {
                sidecarFileWriter?.write(jsonLine + "\n")
                sidecarFileWriter?.flush()
            } catch (ignored: Exception) {}
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        closeSidecar()
    }

    private fun closeSidecar() {
        try {
            sidecarFileWriter?.flush()
            sidecarFileWriter?.close()
        } catch (ignored: Exception) {}
        sidecarFileWriter = null
    }
}
