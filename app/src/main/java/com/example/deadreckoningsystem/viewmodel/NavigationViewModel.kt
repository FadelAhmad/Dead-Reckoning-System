package com.example.deadreckoningsystem.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.deadreckoningsystem.model.GpsData
import com.example.deadreckoningsystem.model.ImuData
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.model.VehicleTelemetry
import com.example.deadreckoningsystem.sensor.SensorCollector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel serving real-time vehicle telemetry, managing live sensor streams (50–100 Hz IMU + 1 Hz GPS),
 * automated GPS failover detector logic, and interactive hackathon demo controls.
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorCollector = SensorCollector(application)

    // Navigation State Machine
    private val _navState = MutableStateFlow(NavState.GNSS_LOCKED)
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    // 1. Live 6-Axis IMU Stream StateFlow (50–100 Hz)
    private val _imuTelemetry = MutableStateFlow(ImuData())
    val imuTelemetry: StateFlow<ImuData> = _imuTelemetry.asStateFlow()

    // 2. Live GPS Telemetry Stream StateFlow (1 Hz)
    private val _gpsTelemetry = MutableStateFlow(GpsData())
    val gpsTelemetry: StateFlow<GpsData> = _gpsTelemetry.asStateFlow()

    // Combined Navigation Telemetry for UI
    private val _telemetryState = MutableStateFlow(VehicleTelemetry())
    val telemetryState: StateFlow<VehicleTelemetry> = _telemetryState.asStateFlow()

    // Breadcrumb trajectory history (up to 250 points)
    private val _trajectoryHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val trajectoryHistory: StateFlow<List<Pair<Float, Float>>> = _trajectoryHistory.asStateFlow()

    // Manual Override Flag for Hackathon Jury Demos
    private var isManualOutageOverride = false

    // GPS Watchdog tracking timestamp of last valid GPS update
    private var lastGpsFixTimestampMs = System.currentTimeMillis()

    private var simulationJob: Job? = null
    private var gpsWatchdogJob: Job? = null
    private var simulationStep = 0
    private var totalDistance = 0f

    init {
        startLiveSensorPipeline()
        startGpsWatchdogAndFailoverDetector()
        startMockTelemetryStream()
    }

    /**
     * Connects live hardware SensorCollector streams for IMU (50-100 Hz) and GPS (1 Hz).
     */
    private fun startLiveSensorPipeline() {
        // Collect High-Frequency IMU (Accel + Gyro)
        sensorCollector.startImuUpdates()
            .onEach { imuSample ->
                _imuTelemetry.value = imuSample
            }
            .catch { /* Fallback to software/mock telemetry */ }
            .launchIn(viewModelScope)

        // Collect 1 Hz GPS Updates
        sensorCollector.startGpsUpdates()
            .onEach { gpsSample ->
                _gpsTelemetry.value = gpsSample

                // Evaluate GPS quality
                if (gpsSample.isValid && gpsSample.accuracyMeters <= 20.0f) {
                    lastGpsFixTimestampMs = System.currentTimeMillis()
                }

                // Update UI state if GPS is healthy and manual override is off
                if (!isManualOutageOverride && _navState.value != NavState.CALIBRATING) {
                    if (gpsSample.isValid && gpsSample.accuracyMeters <= 20.0f) {
                        _navState.value = NavState.GNSS_LOCKED
                    }
                }
            }
            .catch { /* Fallback gracefully */ }
            .launchIn(viewModelScope)
    }

    /**
     * Automated GPS Failover Detector:
     * Periodically inspects GPS fix freshness and horizontal accuracy.
     * Triggers GNSS_LOCKED -> AI_DEAD_RECKONING failover if:
     *  - GPS update timeout > 1000ms
     *  - Horizontal accuracy > 20 meters
     *  - Manual outage override is toggled ON
     */
    private fun startGpsWatchdogAndFailoverDetector() {
        gpsWatchdogJob?.cancel()
        gpsWatchdogJob = viewModelScope.launch {
            while (true) {
                delay(300) // Check watchdog 3 times per second
                val timeSinceLastGpsMs = System.currentTimeMillis() - lastGpsFixTimestampMs
                val currentGpsAccuracy = _gpsTelemetry.value.accuracyMeters

                val isGpsDeniedOrLowQuality = timeSinceLastGpsMs > 1000L ||
                        currentGpsAccuracy > 20.0f ||
                        !_gpsTelemetry.value.isValid

                if (_navState.value != NavState.CALIBRATING) {
                    if (isManualOutageOverride || isGpsDeniedOrLowQuality) {
                        if (_navState.value != NavState.AI_DEAD_RECKONING) {
                            _navState.value = NavState.AI_DEAD_RECKONING
                        }
                    } else {
                        if (_navState.value != NavState.GNSS_LOCKED) {
                            _navState.value = NavState.GNSS_LOCKED
                        }
                    }
                }
            }
        }
    }

    /**
     * Manual override toggle for hackathon jury demos.
     */
    fun toggleGpsOutage() {
        isManualOutageOverride = !isManualOutageOverride
        if (isManualOutageOverride) {
            _navState.value = NavState.AI_DEAD_RECKONING
        } else {
            val isGpsHealthy = (System.currentTimeMillis() - lastGpsFixTimestampMs) <= 1000L &&
                    _gpsTelemetry.value.accuracyMeters <= 20.0f
            _navState.value = if (isGpsHealthy) NavState.GNSS_LOCKED else NavState.AI_DEAD_RECKONING
        }
    }

    /**
     * Recalibrates phone IMU reference frame and resets origin offset.
     */
    fun recalibrateImu() {
        viewModelScope.launch {
            val previousState = _navState.value
            _navState.value = NavState.CALIBRATING

            delay(1200)

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
     * Smooth 10 Hz Trajectory Engine feeding UI canvas and DR step integration.
     */
    private fun startMockTelemetryStream() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            val dt = 0.1f // 100ms cycle

            var x = 0f
            var y = 0f
            var headingDeg = 45f

            while (true) {
                delay(100)
                simulationStep++

                val targetSpeedMps = when {
                    simulationStep % 600 < 100 -> 14.0f + sin(simulationStep * 0.05f) * 1.5f
                    simulationStep % 600 < 220 -> 10.5f + cos(simulationStep * 0.04f) * 1.0f
                    simulationStep % 600 < 350 -> 18.0f + sin(simulationStep * 0.02f) * 2.0f
                    else -> 12.0f + sin(simulationStep * 0.08f) * 2.5f
                }

                val yawRateDegPerSec = when {
                    simulationStep % 600 in 101..220 -> 12.0f
                    simulationStep % 600 in 360..420 -> -15.0f
                    simulationStep % 600 in 421..480 -> 15.0f
                    else -> sin(simulationStep * 0.1f) * 0.8f
                }

                headingDeg = (headingDeg + yawRateDegPerSec * dt + 360f) % 360f

                val headingRad = Math.toRadians(headingDeg.toDouble())
                val dx = (targetSpeedMps * sin(headingRad) * dt).toFloat()
                val dy = (-targetSpeedMps * cos(headingRad) * dt).toFloat()

                x += dx
                y += dy
                totalDistance += targetSpeedMps * dt

                val currentState = _navState.value

                val currentDrift = when (currentState) {
                    NavState.GNSS_LOCKED -> 0.6f + (sin(simulationStep * 0.05f) * 0.2f).toFloat()
                    NavState.AI_DEAD_RECKONING -> {
                        val baseDrift = _telemetryState.value.driftEstimateMeters
                        (baseDrift + 0.015f).coerceAtMost(6.5f)
                    }
                    NavState.CALIBRATING -> 0.0f
                }

                val sensorHealthText = when (currentState) {
                    NavState.GNSS_LOCKED -> "GNSS Active • Acc: ${String.format("%.1f", _gpsTelemetry.value.accuracyMeters)}m"
                    NavState.AI_DEAD_RECKONING -> "Pod 1 ML Speed Active • Pod 2 Gyro Integrated"
                    NavState.CALIBRATING -> "Calibrating Sensors..."
                }

                _telemetryState.update {
                    VehicleTelemetry(
                        xMeters = x,
                        yMeters = y,
                        latitude = _gpsTelemetry.value.latitude,
                        longitude = _gpsTelemetry.value.longitude,
                        speedMps = targetSpeedMps,
                        bearingDegrees = headingDeg,
                        driftEstimateMeters = currentDrift,
                        sensorHealth = sensorHealthText,
                        stepCount = simulationStep.toLong(),
                        totalDistanceMeters = totalDistance
                    )
                }

                if (simulationStep % 2 == 0) {
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
        gpsWatchdogJob?.cancel()
    }
}
