package com.example.deadreckoningsystem.filter

import com.example.deadreckoningsystem.model.ImuData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 9-State Extended Kalman Filter for Dead Reckoning with Bias Estimation, ZUPT,
 * Confidence-Decayed NHC, Innovation-Gated GPS Updates, and Learned Speed Fusion.
 *
 * State Vector (9D): [px, py, pz, vx, vy, vz, bax, bay, baz]
 */
class KalmanFilter(
    private val qBias: Double = 0.00001,
    var chi2GpsThreshold: Double = 9.21,
    var minSpeedForNhc: Double = 1.0,
    var yawOffsetAlpha: Double = 0.02
) {
    // 9D State Vector
    val x = DoubleArray(9)

    // 9x9 Covariance Matrix (stored as 1D array size 81 for efficiency)
    val P = DoubleArray(81)

    // 9x9 Process Noise Matrix
    private val Q = DoubleArray(81)

    var yawOffset: Double = 0.0
    var yawOffsetInitialized: Boolean = false
    var timeInBlackout: Double = 0.0
    var nGpsRejected: Long = 0L

    private val rNhcBase = 0.10 * 0.10
    private val rNhcGrowth = 0.02

    init {
        reset(0.0, 0.0)
    }

    /**
     * Initializes state vector and covariance matrices.
     */
    fun reset(initialPx: Double, initialPy: Double) {
        x.fill(0.0)
        x[0] = initialPx
        x[1] = initialPy

        // Initial P
        P.fill(0.0)
        P[0 * 9 + 0] = 10.0; P[1 * 9 + 1] = 10.0; P[2 * 9 + 2] = 10.0 // Position
        P[3 * 9 + 3] = 1.0;  P[4 * 9 + 4] = 1.0;  P[5 * 9 + 5] = 1.0  // Velocity
        P[6 * 9 + 6] = 0.5;  P[7 * 9 + 7] = 0.5;  P[8 * 9 + 8] = 0.5  // Accel Bias

        // Process Noise Q
        Q.fill(0.0)
        Q[0 * 9 + 0] = 0.01;    Q[1 * 9 + 1] = 0.01;    Q[2 * 9 + 2] = 0.01
        Q[3 * 9 + 3] = 0.1;     Q[4 * 9 + 4] = 0.1;     Q[5 * 9 + 5] = 0.1
        Q[6 * 9 + 6] = qBias;   Q[7 * 9 + 7] = qBias;   Q[8 * 9 + 8] = qBias

        yawOffset = 0.0
        yawOffsetInitialized = false
        timeInBlackout = 0.0
        nGpsRejected = 0L
    }

    /**
     * Main single-tick function called on every IMU sample.
     */
    fun tick(
        dt: Double,
        imuData: ImuData,
        rawYawRad: Double,
        stationary: Boolean,
        gpsAvailable: Boolean,
        isGoodQualityGps: Boolean,
        isDistinctGpsFix: Boolean,
        gpsPx: Double,
        gpsPy: Double,
        rGpsVariance: Double = 225.0, // 15.0^2
        predictedSpeedMps: Float? = null,
        rSpeedVariance: Double = 64.0 // 8.0^2
    ) {
        if (dt <= 0.0) return

        // --- 1. Prediction Step (Always Runs) ---
        val cOff = cos(yawOffset)
        val sOff = sin(yawOffset)

        // Accelerometer in map frame
        val aMapX = imuData.accelX * cOff - imuData.accelY * sOff
        val aMapY = imuData.accelX * sOff + imuData.accelY * cOff
        val aMapZ = imuData.accelZ.toDouble()

        // Subtract bias
        val aCorrX = aMapX - x[6]
        val aCorrY = aMapY - x[7]
        val aCorrZ = aMapZ - x[8]

        // State propagation
        x[0] += x[3] * dt + 0.5 * aCorrX * dt * dt
        x[1] += x[4] * dt + 0.5 * aCorrY * dt * dt
        x[2] += x[5] * dt + 0.5 * aCorrZ * dt * dt

        x[3] += aCorrX * dt
        x[4] += aCorrY * dt
        x[5] += aCorrZ * dt

        // Covariance Propagation P = F * P * F^T + Q
        // F matrix construction: F = I_9, F[0:3, 3:6] = I_3 * dt, F[0:3, 6:9] = -0.5 * I_3 * dt^2, F[3:6, 6:9] = -I_3 * dt
        propagateCovariance(dt)

        val speedMag = sqrt(x[3] * x[3] + x[4] * x[4])

        // --- 2. GPS Update Branch ---
        if (gpsAvailable && isGoodQualityGps && isDistinctGpsFix) {
            val y0 = gpsPx - x[0]
            val y1 = gpsPy - x[1]

            // S_gps = P_pos + R_gps
            val s00 = P[0 * 9 + 0] + rGpsVariance
            val s01 = P[0 * 9 + 1]
            val s10 = P[1 * 9 + 0]
            val s11 = P[1 * 9 + 1] + rGpsVariance

            val detS = s00 * s11 - s01 * s10
            if (Math.abs(detS) > 1e-12) {
                val invS00 = s11 / detS
                val invS01 = -s01 / detS
                val invS10 = -s10 / detS
                val invS11 = s00 / detS

                val mahalanobisSq = y0 * (invS00 * y0 + invS01 * y1) + y1 * (invS10 * y0 + invS11 * y1)

                if (mahalanobisSq <= chi2GpsThreshold) {
                    // Accept GPS fix: Compute K (9x2) = P[:, 0:2] * invS
                    val K = DoubleArray(18) // 9 rows x 2 cols
                    for (i in 0 until 9) {
                        val p0 = P[i * 9 + 0]
                        val p1 = P[i * 9 + 1]
                        K[i * 2 + 0] = p0 * invS00 + p1 * invS10
                        K[i * 2 + 1] = p0 * invS01 + p1 * invS11
                    }

                    // Update state x = x + K * y
                    for (i in 0 until 9) {
                        x[i] += K[i * 2 + 0] * y0 + K[i * 2 + 1] * y1
                    }

                    // Update covariance P = P - K * S * K^T
                    updateCovariance2D(K, s00, s01, s10, s11)

                    timeInBlackout = 0.0

                    if (speedMag > minSpeedForNhc) {
                        val mapHeading = atan2(x[4], x[3])
                        val rawOffset = atan2(sin(mapHeading - rawYawRad), cos(mapHeading - rawYawRad))
                        if (!yawOffsetInitialized) {
                            yawOffset = rawOffset
                            yawOffsetInitialized = true
                        } else {
                            val sinAvg = (1.0 - yawOffsetAlpha) * sin(yawOffset) + yawOffsetAlpha * sin(rawOffset)
                            val cosAvg = (1.0 - yawOffsetAlpha) * cos(yawOffset) + yawOffsetAlpha * cos(rawOffset)
                            yawOffset = atan2(sinAvg, cosAvg)
                        }
                    }
                } else {
                    nGpsRejected++
                }
            }
        } else {
            timeInBlackout += dt
        }

        // --- 3. ZUPT Branch ---
        if (stationary) {
            val rZupt = 0.05 * 0.05
            for (vIdx in 0..2) {
                val stateIdx = 3 + vIdx
                val y = 0.0 - x[stateIdx]
                val S = P[stateIdx * 9 + stateIdx] + rZupt
                if (S > 1e-12) {
                    val K = DoubleArray(9) { i -> P[i * 9 + stateIdx] / S }
                    for (i in 0 until 9) {
                        x[i] += K[i] * y
                    }
                    updateCovariance1D(K, S)
                }
            }
        }

        // --- 4. NHC Branch ---
        if (!stationary && speedMag > minSpeedForNhc && yawOffsetInitialized) {
            val correctedYaw = rawYawRad + yawOffset
            val hx = cos(correctedYaw)
            val hy = sin(correctedYaw)

            // H_nhc = [0, 0, 0, -hy, hx, 0, 0, 0, 0]
            val rNhcCurrent = rNhcBase + rNhcGrowth * (timeInBlackout * timeInBlackout)

            // y_n = 0.0 - H_nhc * x = 0.0 - (-hy * vx + hx * vy)
            val yN = 0.0 - (-hy * x[3] + hx * x[4])

            // S_n = H_nhc * P * H_nhc^T + R_nhc
            val p33 = P[3 * 9 + 3]; val p34 = P[3 * 9 + 4]
            val p43 = P[4 * 9 + 3]; val p44 = P[4 * 9 + 4]
            val Sn = (-hy * (-hy * p33 + hx * p43) + hx * (-hy * p34 + hx * p44)) + rNhcCurrent

            if (Sn > 1e-12) {
                val K = DoubleArray(9) { i ->
                    (-hy * P[i * 9 + 3] + hx * P[i * 9 + 4]) / Sn
                }
                for (i in 0 until 9) {
                    x[i] += K[i] * yN
                }
                updateCovariance1D(K, Sn)
            }
        }

        // --- 5. TFLite Learned Speed Fusion Branch ---
        if (predictedSpeedMps != null && !gpsAvailable && !stationary && speedMag > 0.5) {
            val heading = atan2(x[4], x[3])
            val hx = cos(heading)
            val hy = sin(heading)

            // H_speed = [0, 0, 0, hx, hy, 0, 0, 0, 0]
            val yS = predictedSpeedMps.toDouble() - (hx * x[3] + hy * x[4])

            val p33 = P[3 * 9 + 3]; val p34 = P[3 * 9 + 4]
            val p43 = P[4 * 9 + 3]; val p44 = P[4 * 9 + 4]
            val Ss = (hx * (hx * p33 + hy * p43) + hy * (hx * p34 + hy * p44)) + rSpeedVariance

            if (Ss > 1e-12) {
                val K = DoubleArray(9) { i ->
                    (hx * P[i * 9 + 3] + hy * P[i * 9 + 4]) / Ss
                }
                for (i in 0 until 9) {
                    x[i] += K[i] * yS
                }
                updateCovariance1D(K, Ss)
            }
        }
    }

    private fun propagateCovariance(dt: Double) {
        val dt2_05 = 0.5 * dt * dt
        val temp = DoubleArray(81)

        // F = I_9; F[0:3,3:6] = dt; F[0:3,6:9] = -dt2_05; F[3:6,6:9] = -dt
        // Multiply F * P into temp
        for (i in 0 until 9) {
            for (j in 0 until 9) {
                var sum = P[i * 9 + j]
                if (i in 0..2) {
                    sum += dt * P[(i + 3) * 9 + j] - dt2_05 * P[(i + 6) * 9 + j]
                } else if (i in 3..5) {
                    sum -= dt * P[(i + 3) * 9 + j]
                }
                temp[i * 9 + j] = sum
            }
        }

        // Multiply temp * F^T + Q into P
        for (i in 0 until 9) {
            for (j in 0 until 9) {
                var sum = temp[i * 9 + j]
                if (j in 0..2) {
                    sum += dt * temp[i * 9 + (j + 3)] - dt2_05 * temp[i * 9 + (j + 6)]
                } else if (j in 3..5) {
                    sum -= dt * temp[i * 9 + (j + 3)]
                }
                P[i * 9 + j] = sum + Q[i * 9 + j]
            }
        }
    }

    private fun updateCovariance2D(K: DoubleArray, s00: Double, s01: Double, s10: Double, s11: Double) {
        // P_new = P - K * S * K^T
        val KS = DoubleArray(18)
        for (i in 0 until 9) {
            val k0 = K[i * 2 + 0]
            val k1 = K[i * 2 + 1]
            KS[i * 2 + 0] = k0 * s00 + k1 * s10
            KS[i * 2 + 1] = k0 * s01 + k1 * s11
        }

        for (i in 0 until 9) {
            for (j in 0 until 9) {
                val ks0 = KS[i * 2 + 0]
                val ks1 = KS[i * 2 + 1]
                val k0_j = K[j * 2 + 0]
                val k1_j = K[j * 2 + 1]
                P[i * 9 + j] -= (ks0 * k0_j + ks1 * k1_j)
            }
        }
    }

    private fun updateCovariance1D(K: DoubleArray, S: Double) {
        for (i in 0 until 9) {
            for (j in 0 until 9) {
                P[i * 9 + j] -= K[i] * S * K[j]
            }
        }
    }
}
