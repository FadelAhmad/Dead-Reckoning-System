package com.example.deadreckoningsystem

import com.example.deadreckoningsystem.model.GpsData
import com.example.deadreckoningsystem.sensor.GpsAvailabilityDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsAvailabilityDetectorTest {

    private lateinit var detector: GpsAvailabilityDetector

    @Before
    fun setUp() {
        detector = GpsAvailabilityDetector(
            maxStalenessMs = 2500L,
            maxAccuracyMeters = 20.0f,
            debounceThreshold = 2
        )
    }

    @Test
    fun testHealthyFixRequiresDebounceToLock() {
        val now = 1000000L
        val goodFix1 = GpsData(latitude = 28.61, longitude = 77.20, accuracyMeters = 5f, timestampMs = now, isValid = true)
        val goodFix2 = GpsData(latitude = 28.6101, longitude = 77.2001, accuracyMeters = 6f, timestampMs = now + 1000L, isValid = true)

        // 1st sample: should not yet lock (debounce = 2)
        val (good1, _) = detector.processGpsFix(goodFix1, now)
        assertTrue(good1)
        assertFalse("Should require 2 consecutive fixes", detector.gpsAvailable.value)

        // 2nd consecutive good sample: should lock
        val (good2, _) = detector.processGpsFix(goodFix2, now + 1000L)
        assertTrue(good2)
        assertTrue("Should be locked after 2 consecutive good fixes", detector.gpsAvailable.value)
    }

    @Test
    fun testDegradedAccuracyTriggersOutage() {
        val now = 1000000L
        // Lock initially
        detector.processGpsFix(GpsData(latitude = 28.61, longitude = 77.20, accuracyMeters = 5f, timestampMs = now, isValid = true), now)
        detector.processGpsFix(GpsData(latitude = 28.6101, longitude = 77.2001, accuracyMeters = 5f, timestampMs = now + 1000L, isValid = true), now + 1000L)
        assertTrue(detector.gpsAvailable.value)

        // Inaccurate fixes (> 20 meters) e.g. entering urban canyon / tunnel approach
        val badFix1 = GpsData(latitude = 28.6102, longitude = 77.2002, accuracyMeters = 45f, timestampMs = now + 2000L, isValid = true)
        val badFix2 = GpsData(latitude = 28.6103, longitude = 77.2003, accuracyMeters = 60f, timestampMs = now + 3000L, isValid = true)

        detector.processGpsFix(badFix1, now + 2000L)
        detector.processGpsFix(badFix2, now + 3000L)

        assertFalse("High accuracy error must trigger GPS outage", detector.gpsAvailable.value)
    }

    @Test
    fun testWatchdogDetectsStaleFixes() {
        val now = 1000000L
        // Lock initially
        detector.processGpsFix(GpsData(latitude = 28.61, longitude = 77.20, accuracyMeters = 5f, timestampMs = now, isValid = true), now)
        detector.processGpsFix(GpsData(latitude = 28.6101, longitude = 77.2001, accuracyMeters = 5f, timestampMs = now + 1000L, isValid = true), now + 1000L)
        assertTrue(detector.gpsAvailable.value)

        // No new fixes arrive (e.g. inside deep underground tunnel)
        // Check staleness watchdog beyond maxStalenessMs (2500ms)
        detector.checkStalenessWatchdog(now + 4000L)
        detector.checkStalenessWatchdog(now + 4500L)

        assertFalse("Watchdog must drop GPS availability when fixes stop arriving", detector.gpsAvailable.value)
    }

    @Test
    fun testProviderDisabledDropsAvailabilityImmediately() {
        val now = 1000000L
        detector.processGpsFix(GpsData(latitude = 28.61, longitude = 77.20, accuracyMeters = 5f, timestampMs = now, isValid = true), now)
        detector.processGpsFix(GpsData(latitude = 28.6101, longitude = 77.2001, accuracyMeters = 5f, timestampMs = now + 1000L, isValid = true), now + 1000L)
        assertTrue(detector.gpsAvailable.value)

        // Disable provider
        detector.setProviderAvailable(false)
        assertFalse("Disabling provider must immediately drop GPS availability", detector.gpsAvailable.value)
    }
}