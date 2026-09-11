package com.example.deadreckoningsystem

import com.example.deadreckoningsystem.filter.RetrospectiveRecalibrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetrospectiveRecalibratorTest {

    private lateinit var recalibrator: RetrospectiveRecalibrator

    @Before
    fun setUp() {
        recalibrator = RetrospectiveRecalibrator()
    }

    @Test
    fun testRecalibrationEliminatesClosureDriftError() {
        // Vehicle enters 100m tunnel at origin (0, 0)
        recalibrator.onBlackoutStarted(0f, 0f)
        assertTrue(recalibrator.isBlackoutActive)

        // Drive 10 steps of 10 meters each at 10 m/s (dt = 1s), accumulating small lateral drift
        // Path drifts from y = 0 to y = 5 meters at tunnel exit
        for (step in 1..10) {
            val drX = step * 10f
            val drY = step * 0.5f // Accumulated 5.0m drift at exit
            recalibrator.recordStep(drX, drY, speedMps = 10f, dt = 1f)
        }

        // True GPS fix reacquired at tunnel exit: (100, 0) -> true road has y = 0
        val smoothed = recalibrator.onGpsReacquired(100f, 0f)

        assertFalse(recalibrator.isBlackoutActive)
        assertEquals(11, smoothed.size)

        // 1. Entrance point (index 0) must remain at (0, 0)
        assertEquals(0f, smoothed.first().first, 1e-4f)
        assertEquals(0f, smoothed.first().second, 1e-4f)

        // 2. Exit point (index 10) must exactly match the reacquired fix (100, 0)
        assertEquals(100f, smoothed.last().first, 1e-4f)
        assertEquals(0f, smoothed.last().second, 1e-4f)

        // 3. Midpoint (index 5) should have half the error corrected
        assertEquals(50f, smoothed[5].first, 1e-4f)
        assertEquals(0f, smoothed[5].second, 1e-4f)
    }

    @Test
    fun testShortBlackoutReturnsFallbackSafely() {
        recalibrator.onBlackoutStarted(10f, 20f)
        val result = recalibrator.onGpsReacquired(12f, 22f)
        assertEquals(1, result.size)
        assertEquals(10f, result[0].first, 1e-4f)
        assertEquals(20f, result[0].second, 1e-4f)
    }

    @Test
    fun testResetClearsState() {
        recalibrator.onBlackoutStarted(0f, 0f)
        recalibrator.recordStep(10f, 0f, 5f, 1f)
        recalibrator.reset()
        assertFalse(recalibrator.isBlackoutActive)
    }
}
