package com.example.deadreckoningsystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.model.VehicleTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel serving real-time 10 Hz vehicle telemetry, state machine management,
 * and mock coordinate playback engine along a predetermined route.
 */
class NavigationViewModel : ViewModel() {

    private val _navState = MutableStateFlow(NavState.GNSS_LOCKED)
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    private val _telemetryState = MutableStateFlow(VehicleTelemetry())
    val telemetryState: StateFlow<VehicleTelemetry> = _telemetryState.asStateFlow()

    // Trajectory history for breadcrumb path rendering (up to 300 points)
    private val _trajectoryHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val trajectoryHistory: StateFlow<List<Pair<Float, Float>>> = _trajectoryHistory.asStateFlow()

    private var simulationJob: Job? = null
    private var simulationStep = 0
    private var totalDistance = 0f

    init {
        startMockTelemetryStream()
    }

    /**
     * Toggles between GNSS_LOCKED and AI_DEAD_RECKONING modes for hackathon demos.
     */
    fun toggleGpsOutage() {
        _navState.update { current ->
            when (current) {
                NavState.GNSS_LOCKED -> NavState.AI_DEAD_RECKONING
                NavState.AI_DEAD_RECKONING -> NavState.GNSS_LOCKED
                NavState.CALIBRATING -> NavState.AI_DEAD_RECKONING
            }
        }
    }

    /**
     * Recalibrates phone IMU reference frame and resets origin offset.
     */
    fun recalibrateImu() {
        viewModelScope.launch {
            val previousState = _navState.value
            _navState.value = NavState.CALIBRATING

            // Simulate 1.2 second sensor calibration sequence
            delay(1200)

            // Reset local origin and drift metrics
            _telemetryState.update {
                it.copy(
                    xMeters = 0f,
                    yMeters = 0f,
                    driftEstimateMeters = 0.5f,
                    sensorHealth = "IMU Calibrated & Zeroed"
                )
            }
            _trajectoryHistory.value = listOf(Pair(0f, 0f))
            _navState.value = if (previousState == NavState.CALIBRATING) NavState.GNSS_LOCKED else previousState
        }
    }

    /**
     * 10 Hz Mock Playback Engine streaming smooth vehicle trajectory.
     */
    private fun startMockTelemetryStream() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            val dt = 0.1f // 100ms interval = 10 Hz

            var x = 0f
            var y = 0f
            var headingDeg = 45f // Initial bearing: Northeast

            while (true) {
                delay(100) // 10 Hz cycle
                simulationStep++

                // Modulate speed and heading dynamically along simulated route:
                // Segment 1: Accelerated straight drive (step 0..100)
                // Segment 2: Smooth 90-degree curve through underground tunnel (step 101..220)
                // Segment 3: Straight tunnel highway (step 221..350)
                // Segment 4: S-curve maneuver (step 351..500)
                val targetSpeedMps = when {
                    simulationStep % 600 < 100 -> 14.0f + sin(simulationStep * 0.05f) * 1.5f // ~50 km/h
                    simulationStep % 600 < 220 -> 10.5f + cos(simulationStep * 0.04f) * 1.0f // ~38 km/h curve
                    simulationStep % 600 < 350 -> 18.0f + sin(simulationStep * 0.02f) * 2.0f // ~65 km/h highway
                    else -> 12.0f + sin(simulationStep * 0.08f) * 2.5f
                }

                // Turning rate (gyroscope yaw rate simulation)
                val yawRateDegPerSec = when {
                    simulationStep % 600 in 101..220 -> 12.0f // Smooth right curve
                    simulationStep % 600 in 360..420 -> -15.0f // Left S-turn
                    simulationStep % 600 in 421..480 -> 15.0f // Right S-turn
                    else -> sin(simulationStep * 0.1f) * 0.8f // Slight road weave
                }

                headingDeg = (headingDeg + yawRateDegPerSec * dt + 360f) % 360f

                // Convert heading to radians (0 deg = North/Up, 90 deg = East/Right)
                val headingRad = Math.toRadians(headingDeg.toDouble())
                val dx = (targetSpeedMps * sin(headingRad) * dt).toFloat()
                val dy = (-targetSpeedMps * cos(headingRad) * dt).toFloat() // Screen Y inverted for up = negative Y or cartesian

                x += dx
                y += dy
                totalDistance += targetSpeedMps * dt

                // Auto-trigger GPS outage simulation on step range 150..380 if auto-demo desired
                val currentState = _navState.value

                // Drift behavior calculation based on mode
                val currentDrift = when (currentState) {
                    NavState.GNSS_LOCKED -> 0.6f + (sin(simulationStep * 0.05f) * 0.2f).toFloat()
                    NavState.AI_DEAD_RECKONING -> {
                        val baseDrift = _telemetryState.value.driftEstimateMeters
                        // Low exponential error growth thanks to AI 1D-CNN filter ZUPT corrections
                        (baseDrift + 0.015f).coerceAtMost(6.5f)
                    }
                    NavState.CALIBRATING -> 0.0f
                }

                val sensorHealthText = when (currentState) {
                    NavState.GNSS_LOCKED -> "GNSS Active • 12 Sats • HDOP 0.9"
                    NavState.AI_DEAD_RECKONING -> "Pod 1 ML Speed Active • Pod 2 Gyro Integrated"
                    NavState.CALIBRATING -> "Calibrating Sensors..."
                }

                _telemetryState.update {
                    VehicleTelemetry(
                        xMeters = x,
                        yMeters = y,
                        speedMps = targetSpeedMps,
                        bearingDegrees = headingDeg,
                        driftEstimateMeters = currentDrift,
                        sensorHealth = sensorHealthText,
                        stepCount = simulationStep.toLong(),
                        totalDistanceMeters = totalDistance
                    )
                }

                // Append trajectory history for breadcrumb path rendering
                if (simulationStep % 2 == 0) { // every 200ms
                    _trajectoryHistory.update { history ->
                        (history + Pair(x, y)).takeLast(250)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
    }
}
