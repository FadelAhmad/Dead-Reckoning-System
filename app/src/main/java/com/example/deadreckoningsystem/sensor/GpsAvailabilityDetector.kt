package com.example.deadreckoningsystem.sensor

import com.example.deadreckoningsystem.model.GpsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * GPS Outage Detector combining fix staleness, horizontal accuracy quality thresholds,
 * provider status, debounce counters, and duplicate fix deduplication.
 */
class GpsAvailabilityDetector(
    private val maxStalenessMs: Long = 2500L,
    private val maxAccuracyMeters: Float = 20.0f,
    private val debounceThreshold: Int = 2
) {
    private val _gpsAvailable = MutableStateFlow(false)
    val gpsAvailable: StateFlow<Boolean> = _gpsAvailable.asStateFlow()

    private var lastFixTimestampMs: Long = 0L
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0
    private var isProviderAvailable: Boolean = true

    private var consecutiveBadSamples = 0
    private var consecutiveGoodSamples = 0

    /**
     * Updates provider availability status (e.g. from FusedLocationProviderClient / LocationCallback).
     */
    fun setProviderAvailable(available: Boolean) {
        isProviderAvailable = available
        if (!available) {
            consecutiveGoodSamples = 0
            _gpsAvailable.value = false
        }
    }

    /**
     * Evaluates an incoming GPS fix and updates [gpsAvailable] with debouncing logic.
     * @return Pair<Boolean, Boolean> where first = fixIsGoodQuality, second = isDistinctFix.
     */
    fun processGpsFix(gpsData: GpsData, currentTimeMs: Long = System.currentTimeMillis()): Pair<Boolean, Boolean> {
        if (!isProviderAvailable) {
            consecutiveGoodSamples = 0
            consecutiveBadSamples++
            if (consecutiveBadSamples >= debounceThreshold) {
                _gpsAvailable.value = false
            }
            return Pair(false, false)
        }

        // Duplicate-fix deduplication check
        val isDistinct = abs(gpsData.latitude - lastLat) > 1e-8 || abs(gpsData.longitude - lastLon) > 1e-8
        if (isDistinct) {
            lastLat = gpsData.latitude
            lastLon = gpsData.longitude
        }

        val isFresh = (currentTimeMs - gpsData.timestampMs) <= maxStalenessMs
        val isAccurate = gpsData.isValid && gpsData.accuracyMeters <= maxAccuracyMeters

        val isGoodQuality = isFresh && isAccurate

        if (isGoodQuality) {
            lastFixTimestampMs = currentTimeMs
            consecutiveBadSamples = 0
            consecutiveGoodSamples++
            if (consecutiveGoodSamples >= debounceThreshold) {
                _gpsAvailable.value = true
            }
        } else {
            consecutiveGoodSamples = 0
            consecutiveBadSamples++
            if (consecutiveBadSamples >= debounceThreshold) {
                _gpsAvailable.value = false
            }
        }

        return Pair(isGoodQuality, isDistinct)
    }

    /**
     * Periodic watchdog tick to detect stale fixes when no new GPS samples arrive.
     */
    fun checkStalenessWatchdog(currentTimeMs: Long = System.currentTimeMillis()) {
        if (lastFixTimestampMs > 0L && (currentTimeMs - lastFixTimestampMs) > maxStalenessMs) {
            consecutiveGoodSamples = 0
            consecutiveBadSamples++
            if (consecutiveBadSamples >= debounceThreshold) {
                _gpsAvailable.value = false
            }
        }
    }

    /**
     * Resets detector state.
     */
    fun reset() {
        lastFixTimestampMs = 0L
        lastLat = 0.0
        lastLon = 0.0
        consecutiveBadSamples = 0
        consecutiveGoodSamples = 0
        _gpsAvailable.value = false
    }
}
