package com.example.deadreckoningsystem.model

/**
 * Real-time telemetry snapshot emitted at 10 Hz by the Navigation ViewModel / DR Engine.
 */
data class VehicleTelemetry(
    val xMeters: Float = 0f,
    val yMeters: Float = 0f,
    val latitude: Double = 28.6139,
    val longitude: Double = 77.2090,
    val speedMps: Float = 12.5f, // speed in meters per second
    val bearingDegrees: Float = 0f, // 0 to 360 degrees
    val driftEstimateMeters: Float = 0.8f,
    val sensorHealth: String = "IMU 100 Hz Nominal",
    val stepCount: Long = 0,
    val totalDistanceMeters: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Speed formatted in km/h to 1 decimal place.
     */
    val speedKmh: Float
        get() = speedMps * 3.6f
}
