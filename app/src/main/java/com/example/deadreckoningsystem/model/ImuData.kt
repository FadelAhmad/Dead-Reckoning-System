package com.example.deadreckoningsystem.model

/**
 * 6-Axis Calibrated IMU snapshot emitted at 50–100 Hz.
 * @param accelX Linear acceleration along X axis in m/s^2.
 * @param accelY Linear acceleration along Y axis in m/s^2.
 * @param accelZ Linear acceleration along Z axis in m/s^2.
 * @param gyroX Angular velocity around X axis in rad/s.
 * @param gyroY Angular velocity around Y axis in rad/s.
 * @param gyroZ Angular velocity around Z axis in rad/s.
 * @param timestampNs System uptime timestamp in nanoseconds.
 */
data class ImuData(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 9.81f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val timestampNs: Long = System.nanoTime()
)
