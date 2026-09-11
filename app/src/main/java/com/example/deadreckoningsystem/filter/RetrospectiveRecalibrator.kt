package com.example.deadreckoningsystem.filter

import kotlin.math.max

/**
 * Stage 7: On-Device Endpoint-Anchored Retrospective Recalibrator.
 *
 * Smooths and recalibrates the historical Dead Reckoning path through a GPS blackout segment
 * (e.g. tunnel or underground structure) the instant a healthy GPS fix is reacquired.
 *
 * Implements speed-profile weighted error redistribution:
 *   fraction(k) = (cumulative_distance_up_to_k) / (total_blackout_distance)
 *   P_corrected(k) = P_dr(k) + fraction(k) * (P_reacquired_gps - P_final_dr)
 */
class RetrospectiveRecalibrator {

    data class BlackoutStep(
        val x: Float,
        val y: Float,
        val speedMps: Float,
        val dt: Float
    )

    private val blackoutSteps = mutableListOf<BlackoutStep>()
    var isBlackoutActive: Boolean = false
        private set

    /**
     * Called when GPS availability drops and Dead Reckoning mode activates.
     */
    fun onBlackoutStarted(initialX: Float, initialY: Float) {
        blackoutSteps.clear()
        blackoutSteps.add(BlackoutStep(initialX, initialY, speedMps = 0f, dt = 0f))
        isBlackoutActive = true
    }

    /**
     * Records a Dead Reckoning sample during active blackout.
     */
    fun recordStep(x: Float, y: Float, speedMps: Float, dt: Float) {
        if (!isBlackoutActive) return
        blackoutSteps.add(BlackoutStep(x, y, max(0f, speedMps), max(0f, dt)))
    }

    /**
     * Called upon GPS signal reacquisition.
     * Recalibrates the accumulated blackout trajectory against the ground-truth reacquired fix.
     *
     * @param reacquiredGpsX Local ENU X meters of the newly reacquired GPS fix.
     * @param reacquiredGpsY Local ENU Y meters of the newly reacquired GPS fix.
     * @return The smoothed, endpoint-anchored sequence of (x, y) coordinates for the blackout segment.
     */
    fun onGpsReacquired(reacquiredGpsX: Float, reacquiredGpsY: Float): List<Pair<Float, Float>> {
        if (!isBlackoutActive || blackoutSteps.size < 2) {
            isBlackoutActive = false
            val fallback = blackoutSteps.map { Pair(it.x, it.y) }
            blackoutSteps.clear()
            return fallback
        }

        val lastStep = blackoutSteps.last()
        val errorX = reacquiredGpsX - lastStep.x
        val errorY = reacquiredGpsY - lastStep.y

        // Compute cumulative distance array along the blackout window
        val cumulativeDist = FloatArray(blackoutSteps.size)
        var totalDist = 0f

        for (i in 1 until blackoutSteps.size) {
            val step = blackoutSteps[i]
            val stepDist = step.speedMps * step.dt
            totalDist += stepDist
            cumulativeDist[i] = totalDist
        }

        val recalibratedPath = ArrayList<Pair<Float, Float>>(blackoutSteps.size)

        for (i in blackoutSteps.indices) {
            val step = blackoutSteps[i]
            val fraction = if (totalDist > 0.5f) {
                (cumulativeDist[i] / totalDist).coerceIn(0f, 1f)
            } else {
                // Fallback to linear index fraction if vehicle was mostly stopped
                (i.toFloat() / (blackoutSteps.size - 1).toFloat()).coerceIn(0f, 1f)
            }

            val correctedX = step.x + fraction * errorX
            val correctedY = step.y + fraction * errorY
            recalibratedPath.add(Pair(correctedX, correctedY))
        }

        isBlackoutActive = false
        blackoutSteps.clear()
        return recalibratedPath
    }

    /**
     * Resets recalibrator state.
     */
    fun reset() {
        blackoutSteps.clear()
        isBlackoutActive = false
    }
}
