package com.example.deadreckoningsystem.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.deadreckoningsystem.model.GpsData
import com.example.deadreckoningsystem.model.ImuData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Service collector managing high-frequency hardware IMU sampling (50–100 Hz)
 * via Android SensorManager and 1 Hz location updates via FusedLocationProviderClient.
 */
class SensorCollector(private val context: Context) {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    /**
     * Emits continuous 6-axis IMU samples (Accelerometer + Gyroscope) at 50–100 Hz.
     */
    fun startImuUpdates(): Flow<ImuData> = callbackFlow {
        if (sensorManager == null) {
            close()
            return@callbackFlow
        }

        val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        val lastLinearAccel = FloatArray(3)
        val lastGyro = FloatArray(3)
        val rotationMatrix = FloatArray(9).apply {
            this[0] = 1f; this[4] = 1f; this[8] = 1f // Identity default
        }
        val orientationValues = FloatArray(3)
        var hasRotation = false

        // Low-pass filter for gravity if linear acceleration sensor isn't available directly
        val isRawAccel = linearAccelSensor?.type == Sensor.TYPE_ACCELEROMETER
        val gravity = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                when (event.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        lastLinearAccel[0] = event.values[0]
                        lastLinearAccel[1] = event.values[1]
                        lastLinearAccel[2] = event.values[2]
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (isRawAccel) {
                            // Standard 90% alpha filter to isolate dynamic linear acceleration from 1G gravity
                            val alpha = 0.8f
                            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

                            lastLinearAccel[0] = event.values[0] - gravity[0]
                            lastLinearAccel[1] = event.values[1] - gravity[1]
                            lastLinearAccel[2] = event.values[2] - gravity[2]
                        }
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        lastGyro[0] = event.values[0]
                        lastGyro[1] = event.values[1]
                        lastGyro[2] = event.values[2]
                    }
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)
                        hasRotation = true
                    }
                }

                // Transform body linear acceleration into world frame (East-North-Up) using rotation matrix
                val worldAx = rotationMatrix[0] * lastLinearAccel[0] + rotationMatrix[1] * lastLinearAccel[1] + rotationMatrix[2] * lastLinearAccel[2]
                val worldAy = rotationMatrix[3] * lastLinearAccel[0] + rotationMatrix[4] * lastLinearAccel[1] + rotationMatrix[5] * lastLinearAccel[2]
                val worldAz = rotationMatrix[6] * lastLinearAccel[0] + rotationMatrix[7] * lastLinearAccel[1] + rotationMatrix[8] * lastLinearAccel[2]

                trySend(
                    ImuData(
                        accelX = if (hasRotation) worldAx else lastLinearAccel[0],
                        accelY = if (hasRotation) worldAy else lastLinearAccel[1],
                        accelZ = if (hasRotation) worldAz else lastLinearAccel[2],
                        gyroX = lastGyro[0],
                        gyroY = lastGyro[1],
                        gyroZ = lastGyro[2],
                        yawRad = orientationValues[0], // Azimuth (-PI to +PI)
                        pitchRad = orientationValues[1],
                        rollRad = orientationValues[2],
                        timestampNs = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        linearAccelSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Emits live 1 Hz GPS location updates from FusedLocationProviderClient.
     */
    @SuppressLint("MissingPermission")
    fun startGpsUpdates(): Flow<GpsData> = callbackFlow {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            trySend(
                GpsData(
                    accuracyMeters = 999f,
                    isValid = false,
                    timestampMs = System.currentTimeMillis()
                )
            )
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 Hz update rate
        ).setMinUpdateIntervalMillis(500L).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(
                        GpsData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitude = loc.altitude,
                            speedMps = loc.speed,
                            bearingDegrees = loc.bearing,
                            accuracyMeters = loc.accuracy,
                            timestampMs = loc.time,
                            isValid = loc.accuracy <= 20.0f
                        )
                    )
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (_: Exception) {
            trySend(
                GpsData(
                    accuracyMeters = 999f,
                    isValid = false,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
