package com.hammertime.app.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.hammertime.app.domain.model.RawImuSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ImuSensorProvider(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    var currentGravityVector = FloatArray(3) { 0f }.apply { this[1] = 9.81f }
        private set

    fun observeImu(): Flow<RawImuSample> = callbackFlow {
        var lastGyroX = 0f
        var lastGyroY = 0f
        var lastGyroZ = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY -> {
                        currentGravityVector = event.values.clone()
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        lastGyroX = event.values[0]
                        lastGyroY = event.values[1]
                        lastGyroZ = event.values[2]
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                        val sample = RawImuSample(
                            accelX = event.values[0],
                            accelY = event.values[1],
                            accelZ = event.values[2],
                            gyroX = lastGyroX,
                            gyroY = lastGyroY,
                            gyroZ = lastGyroZ,
                            timestampNanos = event.timestamp
                        )
                        trySend(sample)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        linearAccelSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST, mainHandler) }
        gyroSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST, mainHandler) }
        gravitySensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL, mainHandler) }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}