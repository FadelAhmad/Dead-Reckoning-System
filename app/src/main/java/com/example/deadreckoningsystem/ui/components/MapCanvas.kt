package com.example.deadreckoningsystem.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.model.VehicleTelemetry
import com.example.deadreckoningsystem.ui.theme.MapGridLine
import com.example.deadreckoningsystem.ui.theme.MapRoadPrimary
import com.example.deadreckoningsystem.ui.theme.MapRoadSecondary
import com.example.deadreckoningsystem.ui.theme.MapTunnelOutline
import com.example.deadreckoningsystem.ui.theme.MapTunnelSegment
import com.example.deadreckoningsystem.ui.theme.NeonCyan
import com.example.deadreckoningsystem.ui.theme.NeonGreen
import com.example.deadreckoningsystem.ui.theme.SlateDarkBg
import com.example.deadreckoningsystem.ui.theme.TextMuted
import com.example.deadreckoningsystem.ui.theme.TrajectoryTrailDr
import com.example.deadreckoningsystem.ui.theme.TrajectoryTrailGnss
import kotlin.math.roundToInt

/**
 * Interactive 2D Navigation Canvas representing street lane geometry,
 * underground tunnel zone, breadcrumb trajectory trail, and smooth vehicle movement.
 */
@Composable
fun MapCanvas(
    telemetry: VehicleTelemetry,
    navState: NavState,
    trajectoryHistory: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier
) {
    // Zoom scale & drag offset states for interactive map pan/zoom
    var userZoomScale by remember { mutableFloatStateOf(3.2f) }
    var userPanX by remember { mutableFloatStateOf(0f) }
    var userPanY by remember { mutableFloatStateOf(0f) }

    // Smooth position interpolation matching 10 Hz emission
    val animatedX by animateFloatAsState(
        targetValue = telemetry.xMeters,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "VehicleXAnimation"
    )
    val animatedY by animateFloatAsState(
        targetValue = telemetry.yMeters,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "VehicleYAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDarkBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    userZoomScale = (userZoomScale * zoom).coerceIn(1.5f, 8.0f)
                    userPanX += pan.x
                    userPanY += pan.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val screenCenterX = canvasWidth / 2f + userPanX
            val screenCenterY = canvasHeight / 2f + userPanY

            // Transform relative world coordinates (meters) to screen pixels
            val worldToScreenX: (Float) -> Float = { worldX ->
                screenCenterX + (worldX - animatedX) * userZoomScale * 10f
            }
            val worldToScreenY: (Float) -> Float = { worldY ->
                screenCenterY + (worldY - animatedY) * userZoomScale * 10f
            }

            // 1. Draw Grid Lines
            val gridStepPx = 80f * (userZoomScale / 3.2f)
            var currentGridX = (userPanX % gridStepPx)
            while (currentGridX < canvasWidth) {
                drawLine(
                    color = MapGridLine,
                    start = Offset(currentGridX, 0f),
                    end = Offset(currentGridX, canvasHeight),
                    strokeWidth = 1f
                )
                currentGridX += gridStepPx
            }

            var currentGridY = (userPanY % gridStepPx)
            while (currentGridY < canvasHeight) {
                drawLine(
                    color = MapGridLine,
                    start = Offset(0f, currentGridY),
                    end = Offset(canvasWidth, currentGridY),
                    strokeWidth = 1f
                )
                currentGridY += gridStepPx
            }

            // 2. Draw Simulated Street / Tunnel Overlay Route Network
            val routeWidthPx = 36f * (userZoomScale / 3.2f)

            // Simulated underground tunnel zone indicator
            val tunnelStartScreenY = worldToScreenY(30f)
            val tunnelEndScreenY = worldToScreenY(120f)
            val tunnelStartScreenX = worldToScreenX(-40f)
            val tunnelEndScreenX = worldToScreenX(180f)

            // Tunnel shaded region
            drawRect(
                color = MapTunnelSegment,
                topLeft = Offset(tunnelStartScreenX - 50f, tunnelStartScreenY),
                size = androidx.compose.ui.geometry.Size(
                    tunnelEndScreenX - tunnelStartScreenX + 100f,
                    tunnelEndScreenY - tunnelStartScreenY
                )
            )

            // Tunnel hatch outline
            drawRect(
                color = MapTunnelOutline,
                topLeft = Offset(tunnelStartScreenX - 50f, tunnelStartScreenY),
                size = androidx.compose.ui.geometry.Size(
                    tunnelEndScreenX - tunnelStartScreenX + 100f,
                    tunnelEndScreenY - tunnelStartScreenY
                ),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
            )

            // Road Base Network
            val roadPath = Path().apply {
                moveTo(worldToScreenX(-80f), worldToScreenY(-80f))
                lineTo(worldToScreenX(0f), worldToScreenY(0f))
                lineTo(worldToScreenX(80f), worldToScreenY(80f))
                lineTo(worldToScreenX(160f), worldToScreenY(120f))
            }

            drawPath(
                path = roadPath,
                color = MapRoadPrimary,
                style = Stroke(width = routeWidthPx)
            )

            // Lane Divider Dashed Center Line
            drawPath(
                path = roadPath,
                color = MapRoadSecondary,
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                )
            )

            // 3. Draw Breadcrumb Trajectory History
            if (trajectoryHistory.size > 1) {
                val trajectoryPath = Path()
                val firstPt = trajectoryHistory.first()
                trajectoryPath.moveTo(worldToScreenX(firstPt.first), worldToScreenY(firstPt.second))

                for (i in 1 until trajectoryHistory.size) {
                    val pt = trajectoryHistory[i]
                    trajectoryPath.lineTo(worldToScreenX(pt.first), worldToScreenY(pt.second))
                }

                val trailColor = if (navState == NavState.AI_DEAD_RECKONING) TrajectoryTrailDr else TrajectoryTrailGnss
                val trailStrokeWidth = 6.dp.toPx()

                drawPath(
                    path = trajectoryPath,
                    color = trailColor,
                    style = Stroke(width = trailStrokeWidth)
                )
            }
        }

        // 4. Centered Smooth Vehicle Marker Cursor
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Apply pan offset relative to user drag
            Box(
                modifier = Modifier.offset {
                    IntOffset(userPanX.roundToInt(), userPanY.roundToInt())
                }
            ) {
                VehicleMarker(
                    bearingDegrees = telemetry.bearingDegrees,
                    navState = navState,
                    size = 64.dp
                )
            }
        }

        // 5. Compass & Scale Overlay (Top Left below HUD)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 110.dp, start = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Compass North",
                tint = if (navState == NavState.AI_DEAD_RECKONING) NeonCyan else NeonGreen,
                modifier = Modifier.size(28.dp)
            )
        }

        // Map Scale Indicator (Bottom Left above control panel)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 120.dp, start = 16.dp)
        ) {
            Text(
                text = "SCALE 1 : ${(1000 / userZoomScale).roundToInt()}m  •  2D ENU FRAME",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextMuted
            )
        }
    }
}
