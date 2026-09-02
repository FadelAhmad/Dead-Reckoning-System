package com.example.deadreckoningsystem.model

/**
 * Navigation state machine representing operational mode of the Dead Reckoning System.
 */
enum class NavState {
    /**
     * Satellite GPS is healthy (10 Hz cursor updates, green indicator).
     */
    GNSS_LOCKED,

    /**
     * GPS signal is denied or lost (underground tunnel/parking/urban canyon);
     * AI-ML dead reckoning engine active (amber/cyan indicator, lane-level tracking).
     */
    AI_DEAD_RECKONING,

    /**
     * Standing by or aligning phone reference frame / resetting origin coordinates.
     */
    CALIBRATING
}
