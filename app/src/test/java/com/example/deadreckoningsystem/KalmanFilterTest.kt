package com.example.deadreckoningsystem

import com.example.deadreckoningsystem.filter.KalmanFilter
import com.example.deadreckoningsystem.model.ImuData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KalmanFilterTest {

    private lateinit var kf: KalmanFilter

    @Before
    fun setUp() {
        kf = KalmanFilter(qBias = 1e-5, chi2GpsThreshold = 9.21, minSpeedForNhc = 1.0)
        kf.reset(0.0, 0.0)
    }

    @Test
    fun testPredictionStepPropagatesPositionWithVelocity() {
        // Initialize with initial forward velocity vx = 10 m/s (East)
        kf.x[3] = 10.0
        val dt = 0.1 // 100ms tick
        val imu = ImuData(accelX = 0f, accelY = 0f, accelZ = 0f)

        kf.tick(
            dt = dt,
            imuData = imu,
            rawYawRad = 0.0,
            stationary = false,
            gpsAvailable = false,
            isGoodQualityGps = false,
            isDistinctGpsFix = false,
            gpsPx = 0.0,
            gpsPy = 0.0
        )

        // After 0.1s at 10 m/s, position should be approximately 1.0 meter
        assertEquals(1.0, kf.x[0], 0.05)
        assertEquals(0.0, kf.x[1], 0.01)
        assertEquals(10.0, kf.x[3], 0.1)
    }

    @Test
    fun testZuptLocksDriftWhenStationary() {
        // Give vehicle small residual drift velocity
        kf.x[3] = 0.8
        kf.x[4] = 0.5
        val dt = 0.02

        // Run 20 stationary ticks with ZUPT active
        repeat(20) {
            kf.tick(
                dt = dt,
                imuData = ImuData(0f, 0f, 0f),
                rawYawRad = 0.0,
                stationary = true,
                gpsAvailable = false,
                isGoodQualityGps = false,
                isDistinctGpsFix = false,
                gpsPx = 0.0,
                gpsPy = 0.0
            )
        }

        // Velocities should be heavily attenuated by ZUPT updates
        assertTrue("vx should be pinned near 0: ${kf.x[3]}", Math.abs(kf.x[3]) < 0.15)
        assertTrue("vy should be pinned near 0: ${kf.x[4]}", Math.abs(kf.x[4]) < 0.15)
    }

    @Test
    fun testGpsInnovationGatingRejectsWildOutlier() {
        // Vehicle at origin (0, 0)
        val initialRejectedCount = kf.nGpsRejected

        // Wild outlier GPS fix (500 meters jump)
        kf.tick(
            dt = 0.1,
            imuData = ImuData(0f, 0f, 0f),
            rawYawRad = 0.0,
            stationary = false,
            gpsAvailable = true,
            isGoodQualityGps = true,
            isDistinctGpsFix = true,
            gpsPx = 500.0,
            gpsPy = 500.0,
            rGpsVariance = 25.0
        )

        // Outlier should be rejected via chi2 gating
        assertEquals(initialRejectedCount + 1, kf.nGpsRejected)
        // Position should NOT have jumped to 500
        assertTrue("Position px must not jump to 500: ${kf.x[0]}", kf.x[0] < 5.0)
    }

    @Test
    fun testTfliteSpeedPriorFusesDuringOutage() {
        // Moving vehicle entering tunnel, GPS drops
        kf.x[3] = 12.0 // vx = 12 m/s
        kf.x[4] = 0.0
        val dt = 0.05

        // In tunnel, TFLite model predicts forward speed 15.0 m/s
        val predictedSpeedMps = 15.0f
        repeat(10) {
            kf.tick(
                dt = dt,
                imuData = ImuData(0.1f, 0f, 0f),
                rawYawRad = 0.0,
                stationary = false,
                gpsAvailable = false, // In outage
                isGoodQualityGps = false,
                isDistinctGpsFix = false,
                gpsPx = 0.0,
                gpsPy = 0.0,
                predictedSpeedMps = predictedSpeedMps,
                rSpeedVariance = 16.0
            )
        }

        // Speed should be updated towards the model's 15 m/s prediction
        val currentSpeed = Math.sqrt(kf.x[3] * kf.x[3] + kf.x[4] * kf.x[4])
        assertTrue("Current speed should be updated towards 15 m/s: $currentSpeed", currentSpeed > 12.5)
        assertTrue("Blackout time should accumulate", kf.timeInBlackout > 0.0)
    }
}