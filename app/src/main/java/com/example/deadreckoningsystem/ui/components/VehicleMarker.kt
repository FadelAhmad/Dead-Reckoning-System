package com.example.deadreckoningsystem.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.ui.theme.AmberWarning
import com.example.deadreckoningsystem.ui.theme.NeonCyan
import com.example.deadreckoningsystem.ui.theme.NeonGreen

/**
 * Custom Vehicle Chevron Marker with glowing accent ring, directional arrow,
 * and animated rotation angle.
 */
@Composable
fun VehicleMarker(
    bearingDegrees: Float,
    navState: NavState,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    // Determine accent color according to state
    val accentColor = when (navState) {
        NavState.GNSS_LOCKED -> NeonGreen
        NavState.AI_DEAD_RECKONING -> NeonCyan
        NavState.CALIBRATING -> AmberWarning
    }

    // Smoothly animate rotation bearing
    val animatedBearing by animateFloatAsState(
        targetValue = bearingDegrees,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "VehicleBearingAnimation"
    )

    Canvas(modifier = modifier.size(size)) {
        val width = size.toPx()
        val height = size.toPx()
        val centerX = width / 2f
        val centerY = height / 2f

        rotate(degrees = animatedBearing, pivot = androidx.compose.ui.geometry.Offset(centerX, centerY)) {
            // Glowing outer pulse ring
            drawCircle(
                color = accentColor.copy(alpha = 0.25f),
                radius = width * 0.45f
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.60f),
                radius = width * 0.38f,
                style = Stroke(width = 3.dp.toPx())
            )

            // Vehicle Chevron Arrow Path
            val chevronPath = Path().apply {
                moveTo(centerX, centerY - height * 0.32f) // Top tip
                lineTo(centerX + width * 0.24f, centerY + height * 0.28f) // Bottom right
                lineTo(centerX, centerY + height * 0.16f) // Inner notch
                lineTo(centerX - width * 0.24f, centerY + height * 0.28f) // Bottom left
                close()
            }

            // Fill Chevron
            drawPath(
                path = chevronPath,
                color = accentColor
            )

            // Inner Chevron Outline for crisp modern look
            drawPath(
                path = chevronPath,
                color = Color.White,
                style = Stroke(width = 2.dp.toPx())
            )

            // Center core dot
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }
    }
}
