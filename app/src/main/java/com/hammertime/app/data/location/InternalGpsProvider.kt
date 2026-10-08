package com.hammertime.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.hammertime.app.domain.model.GpsPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class InternalGpsProvider(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var satelliteCount: Int = 0
        private set

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<GpsPoint> = callbackFlow {
        val locationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val speedMph = if (loc.hasSpeed()) loc.speed * 2.23693629 else 0.0
                val timeNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    loc.elapsedRealtimeNanos
                } else {
                    SystemClock.elapsedRealtimeNanos()
                }

                val point = GpsPoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = loc.altitude,
                    speedMph = speedMph,
                    bearingDegrees = if (loc.hasBearing()) loc.bearing else 0f,
                    accuracyMeters = loc.accuracy,
                    timestampNanos = timeNanos,
                    hasBearing = loc.hasBearing()
                )
                trySend(point)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val gnssCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var count = 0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) count++
                    }
                    satelliteCount = count
                }
            }
        } else null

        // Explicitly pass Looper.getMainLooper() so this never fails on background coroutine threads
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                locationManager.registerGnssStatusCallback(mainHandler::post, gnssCallback)
            }
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            try {
                locationManager.removeUpdates(locationListener)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                    locationManager.unregisterGnssStatusCallback(gnssCallback)
                }
            } catch (ignored: Exception) {}
        }
    }
}