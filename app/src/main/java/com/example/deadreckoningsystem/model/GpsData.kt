package com.example.deadreckoningsystem.model

/**
 * Live GPS location update snapshot emitted at 1 Hz by FusedLocationProviderClient.
 * @param latitude Geographic latitude in degrees.
 * @param longitude Geographic longitude in degrees.
 * @param altitude Altitude in meters above WGS84 ellipsoid.
 * @param speedMps Speed over ground in meters per second.
 * @param bearingDegrees Bearing / heading direction in degrees (0 to 360).
 * @param accuracyMeters Horizontal 68% confidence radius accuracy in meters.
 * @param timestampMs Epoch timestamp in milliseconds.
 * @param isValid True if signal is active and below maximum drift threshold.
 */
data class GpsData(
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val altitude: Double = 216.0,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val accuracyMeters: Float = 5.0f,
    val timestampMs: Long = System.currentTimeMillis(),
    val isValid: Boolean = true
)
