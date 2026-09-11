package com.example.deadreckoningsystem.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.deadreckoningsystem.filter.KalmanFilter
import com.example.deadreckoningsystem.ml.SpeedPriorInference
import com.example.deadreckoningsystem.model.GpsData
import com.example.deadreckoningsystem.model.ImuData
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.model.VehicleTelemetry
import com.example.deadreckoningsystem.sensor.GpsAvailabilityDetector
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Production NavigationViewModel driving real hardware GPS and IMU sensor data
 * through a single, unified 9-State Extended Kalman Filter pipeline.
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorCollector = SensorCollector(application)
    val gpsDetector = GpsAvailabilityDetector(
        maxStalenessMs = 5000L, // 5 second staleness window for stationary fixes
        maxAccuracyMeters = 20.0f,
        debounceThreshold = 2
    )
    val kf = KalmanFilter()
    private val speedInference = SpeedPriorInference(application)
    val recalibrator = com.example.deadreckoningsystem.filter.RetrospectiveRecalibrator()

    // Navigation State Machine
    private val _navState = MutableStateFlow(NavState.GNSS_LOCKED)
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    // Live 6-Axis IMU Stream (50–100 Hz)
    private val _imuTelemetry = MutableStateFlow(ImuData())
    val imuTelemetry: StateFlow<ImuData> = _imuTelemetry.asStateFlow()

    // Live GPS Telemetry Stream (1 Hz)
    private val _gpsTelemetry = MutableStateFlow(GpsData())
    val gpsTelemetry: StateFlow<GpsData> = _gpsTelemetry.asStateFlow()

    // Combined UI Telemetry State
    private val _telemetryState = MutableStateFlow(VehicleTelemetry())
    val telemetryState: StateFlow<VehicleTelemetry> = _telemetryState.asStateFlow()

    // Breadcrumb trajectory history (up to 250 points)
    private val _trajectoryHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val trajectoryHistory: StateFlow<List<Pair<Float, Float>>> = _trajectoryHistory.asStateFlow()

    // Manual Override Flag for Manual GPS Control - EXPOSED TO UI
    private val _isManualGpsDisabled = MutableStateFlow(false)
    val isManualGpsDisabled: StateFlow<Boolean> = _isManualGpsDisabled.asStateFlow()

    // Anchor origin for local Cartesian ENU projection (meters)
    private var anchorLatitude: Double? = null
    private var anchorLongitude: Double? = null
    private val _anchorLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val anchorLocation: StateFlow<Pair<Double, Double>?> = _anchorLocation.asStateFlow()

    // Rolling variance history for ZUPT detection
    private val accelHistory = ArrayDeque<Float>()
    private val gyroHistory = ArrayDeque<Float>()

    private var lastImuTimestampNs: Long = 0L
    private var gpsWatchdogJob: Job? = null

    init {
        startLiveSensorPipeline()
        startGpsWatchdog()
    }

    /**
     * Connects live hardware SensorCollector streams for IMU (50-100 Hz) and GPS (1 Hz).
     */
    private fun startLiveSensorPipeline() {
        // High-Frequency IMU Sampling -> Continuous KF Execution
        sensorCollector.startImuUpdates()
            .onEach { imuSample ->
                _imuTelemetry.value = imuSample
                speedInference.pushImuSample(imuSample)

                // Single unified KF tick execution per IMU sample
                processImuSample(imuSample)
            }
            .catch { /* Silent fallback */ }
            .launchIn(viewModelScope)

        // 1 Hz Live GPS Updates
        sensorCollector.startGpsUpdates()
            .onEach { gpsSample ->
                _gpsTelemetry.value = gpsSample

                // Evaluate GPS quality and debounce state through GpsAvailabilityDetector
                val (isGoodQuality, _) = gpsDetector.processGpsFix(gpsSample)

                // Initialize ENU origin on first valid GPS fix
                if (isGoodQuality && (anchorLatitude == null || anchorLongitude == null)) {
                    anchorLatitude = gpsSample.latitude
                    anchorLongitude = gpsSample.longitude
                    _anchorLocation.value = Pair(gpsSample.latitude, gpsSample.longitude)
                    kf.reset(0.0, 0.0)
                }

                // Update UI Navigation State Machine
                syncNavState()
            }
            .catch { /* Silent fallback */ }
            .launchIn(viewModelScope)
    }

    /**
     * Ticks the 9D Kalman Filter on every high-frequency IMU sample.
     */
    private fun processImuSample(imuSample: ImuData) {
        val nowNs = imuSample.timestampNs
        if (lastImuTimestampNs == 0L) {
            lastImuTimestampNs = nowNs
            return
        }

        val dt = ((nowNs - lastImuTimestampNs) / 1e9).coerceIn(0.005, 0.05) // 5ms..50ms
        lastImuTimestampNs = nowNs

        // 1. ZUPT Stationary Detection from rolling linear acceleration variance
        val accMag = sqrt((imuSample.accelX * imuSample.accelX + imuSample.accelY * imuSample.accelY + imuSample.accelZ * imuSample.accelZ).toDouble()).toFloat()
        accelHistory.addLast(accMag)
        if (accelHistory.size > 10) accelHistory.removeFirst()

        val gyroMag = sqrt((imuSample.gyroX * imuSample.gyroX + imuSample.gyroY * imuSample.gyroY + imuSample.gyroZ * imuSample.gyroZ).toDouble()).toFloat()
        gyroHistory.addLast(gyroMag)
        if (gyroHistory.size > 10) gyroHistory.removeFirst()

        val accVar = calculateVariance(accelHistory)
        val gyroVar = calculateVariance(gyroHistory)
        // With gravity removed, stationary linear acceleration magnitude is near 0
        val stationary = accVar < 0.15f && gyroVar < 0.03f && accMag < 0.8f

        // 2. Read GPS state & outage status
        val rawGps = _gpsTelemetry.value
        val isGpsActive = gpsDetector.gpsAvailable.value && !_isManualGpsDisabled.value

        val isGoodQualityGps = isGpsActive && rawGps.isValid && rawGps.accuracyMeters <= 20.0f

        // Convert GPS lat/lon to local ENU meters relative to session origin
        val (gpsPx, gpsPy) = if (anchorLatitude != null && anchorLongitude != null) {
            latLonToEnuMeters(rawGps.latitude, rawGps.longitude, anchorLatitude!!, anchorLongitude!!)
        } else {
            Pair(0.0, 0.0)
        }

        // 3. TFLite Speed Inference (only evaluated during outage when not stationary)
        val (predictedSpeedMps, rSpeedVar) = if (!isGpsActive && !stationary) {
            speedInference.predictSpeedMps()
        } else {
            Pair(null, 64.0f)
        }

        // True compass yaw heading in ENU math convention (0 = East, CCW+)
        // Derived from device rotation vector: yaw = (PI/2 - azimuth)
        val rawYawRad = kotlin.math.atan2(
            kotlin.math.sin(Math.PI / 2.0 - imuSample.yawRad.toDouble()),
            kotlin.math.cos(Math.PI / 2.0 - imuSample.yawRad.toDouble())
        )

        // 4. Tick Kalman Filter with continuous 5-branch logic
        kf.tick(
            dt = dt,
            imuData = imuSample,
            rawYawRad = rawYawRad,
            stationary = stationary,
            gpsAvailable = isGpsActive,
            isGoodQualityGps = isGoodQualityGps,
            isDistinctGpsFix = true,
            gpsPx = gpsPx,
            gpsPy = gpsPy,
            rGpsVariance = (rawGps.accuracyMeters * rawGps.accuracyMeters).toDouble().coerceAtLeast(25.0),
            predictedSpeedMps = predictedSpeedMps,
            rSpeedVariance = rSpeedVar.toDouble()
        )

        // 5. Update UI Telemetry State ONCE per tick from KF State Vector
        val px = kf.x[0].toFloat()
        val py = kf.x[1].toFloat()
        val vx = kf.x[3]
        val vy = kf.x[4]
        val speedMps = sqrt(vx * vx + vy * vy).toFloat()
        val bearingDeg = (Math.toDegrees(Math.atan2(vy, vx)).toFloat() + 360f) % 360f

        val (currentLat, currentLon) = if (anchorLatitude != null && anchorLongitude != null) {
            enuMetersToLatLon(px.toDouble(), py.toDouble(), anchorLatitude!!, anchorLongitude!!)
        } else {
            Pair(rawGps.latitude, rawGps.longitude)
        }

        val driftMeters = sqrt(kf.P[0 * 9 + 0] + kf.P[1 * 9 + 1]).toFloat()

        // Production-ready clean status text
        val sensorHealthText = if (isGpsActive) {
            "GNSS High Accuracy • Active"
        } else {
            "Dead Reckoning System • Active"
        }

        _telemetryState.update {
            VehicleTelemetry(
                xMeters = px,
                yMeters = py,
                latitude = currentLat,
                longitude = currentLon,
                speedMps = speedMps,
                bearingDegrees = bearingDeg,
                driftEstimateMeters = driftMeters,
                sensorHealth = sensorHealthText,
                stepCount = kf.nGpsRejected,
                totalDistanceMeters = sqrt((px * px + py * py).toDouble()).toFloat()
            )
        }

        appendTrajectoryPoint(px, py)

        if (recalibrator.isBlackoutActive) {
            recalibrator.recordStep(px, py, speedMps, dt.toFloat())
        }
    }

    private var blackoutStartIndex: Int = 0

    /**
     * Periodic watchdog checking GPS fix staleness.
     */
    private fun startGpsWatchdog() {
        gpsWatchdogJob?.cancel()
        gpsWatchdogJob = viewModelScope.launch {
            while (true) {
                delay(300)
                gpsDetector.checkStalenessWatchdog()
                syncNavState()
            }
        }
    }

    /**
     * Synchronizes UI NavState state machine from GpsAvailabilityDetector.
     */
    private fun syncNavState() {
        if (_navState.value != NavState.CALIBRATING) {
            val isGpsActive = gpsDetector.gpsAvailable.value && !_isManualGpsDisabled.value
            val targetState = if (isGpsActive) NavState.GNSS_LOCKED else NavState.AI_DEAD_RECKONING

            if (_navState.value != targetState) {
                val prevState = _navState.value
                _navState.value = targetState

                // Stage 7 Retrospective Recalibration Triggers
                if (prevState == NavState.GNSS_LOCKED && targetState == NavState.AI_DEAD_RECKONING) {
                    // Entering blackout: anchor start of segment
                    blackoutStartIndex = _trajectoryHistory.value.size
                    recalibrator.onBlackoutStarted(kf.x[0].toFloat(), kf.x[1].toFloat())
                } else if (prevState == NavState.AI_DEAD_RECKONING && targetState == NavState.GNSS_LOCKED) {
                    // Exiting blackout: reacquired GPS, recalibrate tunnel segment
                    val rawGps = _gpsTelemetry.value
                    if (anchorLatitude != null && anchorLongitude != null && rawGps.isValid) {
                        val (reacquiredX, reacquiredY) = latLonToEnuMeters(rawGps.latitude, rawGps.longitude, anchorLatitude!!, anchorLongitude!!)
                        val recalibratedSegment = recalibrator.onGpsReacquired(reacquiredX.toFloat(), reacquiredY.toFloat())
                        if (recalibratedSegment.isNotEmpty()) {
                            _trajectoryHistory.update { history ->
                                (history.take(blackoutStartIndex) + recalibratedSegment).takeLast(250)
                            }
                        }
                    } else {
                        recalibrator.reset()
                    }
                }
            }
        }
    }

    /**
     * Manual toggle for "Use GPS" control button.
     */
    fun toggleUseGps() {
        _isManualGpsDisabled.value = !_isManualGpsDisabled.value
        syncNavState()
    }

    /**
     * Recalibrates phone IMU reference frame and resets origin offset.
     */
    fun recalibrateImu() {
        viewModelScope.launch {
            _navState.value = NavState.CALIBRATING

            delay(1200)

            if (_gpsTelemetry.value.isValid) {
                anchorLatitude = _gpsTelemetry.value.latitude
                anchorLongitude = _gpsTelemetry.value.longitude
                _anchorLocation.value = Pair(_gpsTelemetry.value.latitude, _gpsTelemetry.value.longitude)
            }
            kf.reset(0.0, 0.0)
            recalibrator.reset()

            _telemetryState.update {
                it.copy(
                    xMeters = 0f,
                    yMeters = 0f,
                    driftEstimateMeters = 0.5f,
                    sensorHealth = "IMU Calibrated & Zeroed"
                )
            }
            _trajectoryHistory.value = listOf(Pair(0f, 0f))
            syncNavState()
        }
    }

    private fun appendTrajectoryPoint(x: Float, y: Float) {
        _trajectoryHistory.update { history ->
            (history + Pair(x, y)).takeLast(250)
        }
    }

    private fun calculateVariance(list: List<Float>): Float {
        if (list.isEmpty()) return 0f
        val mean = list.average().toFloat()
        return list.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    private fun latLonToEnuMeters(lat: Double, lon: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(refLat)
        val metersPerLatDegree = 111320.0
        val metersPerLonDegree = 111320.0 * cos(latRad)

        val xMeters = (lon - refLon) * metersPerLonDegree
        val yMeters = (lat - refLat) * metersPerLatDegree

        return Pair(xMeters, yMeters)
    }

    private fun enuMetersToLatLon(xMeters: Double, yMeters: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(refLat)
        val lat = refLat + (yMeters / 111320.0)
        val lon = refLon + (xMeters / (111320.0 * cos(latRad)))
        return Pair(lat, lon)
    }

    override fun onCleared() {
        super.onCleared()
        gpsWatchdogJob?.cancel()
        speedInference.close()
    }
}
