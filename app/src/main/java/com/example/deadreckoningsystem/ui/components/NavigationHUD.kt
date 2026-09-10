package com.example.deadreckoningsystem.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.model.VehicleTelemetry
import com.example.deadreckoningsystem.ui.theme.AmberWarning
import com.example.deadreckoningsystem.ui.theme.NavySurface
import com.example.deadreckoningsystem.ui.theme.NeonCyan
import com.example.deadreckoningsystem.ui.theme.NeonGreen
import com.example.deadreckoningsystem.ui.theme.SlateBorder
import com.example.deadreckoningsystem.ui.theme.TextPrimary
import com.example.deadreckoningsystem.ui.theme.TextSecondary
import java.util.Locale

/**
 * Top Heads-Up Display (HUD) Status Banner displaying state badge, live vehicle speed in km/h,
 * sensor pipeline health, and drift confidence metrics.
 */
@Composable
fun NavigationHUD(
    telemetry: VehicleTelemetry,
    navState: NavState,
    modifier: Modifier = Modifier
) {
    val (badgeBgColor, badgeTextColor, badgeText, badgeIcon) = when (navState) {
        NavState.GNSS_LOCKED -> Quadruple(
            NeonGreen.copy(alpha = 0.15f),
            NeonGreen,
            "GNSS LOCKED",
            Icons.Default.GpsFixed
        )
        NavState.AI_DEAD_RECKONING -> Quadruple(
            AmberWarning.copy(alpha = 0.20f),
            AmberWarning,
            "AI-DR ACTIVE: GPS DENIED",
            Icons.Default.GpsOff
        )
        NavState.CALIBRATING -> Quadruple(
            NeonCyan.copy(alpha = 0.20f),
            NeonCyan,
            "CALIBRATING IMU...",
            Icons.Default.Sync
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = NavySurface.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            // Top Row: NavState Badge & Drift Confidence Metric
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // State Badge
                Surface(
                    color = badgeBgColor,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeTextColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badgeTextColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            color = badgeTextColor,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                // Drift Confidence Indicator
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Sensor Pipeline",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "Drift: < %.1fm", telemetry.driftEstimateMeters),
                            color = if (telemetry.driftEstimateMeters > 4.0f) AmberWarning else TextPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Speed Display in km/h & Production Status Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Large Speed Readout
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Vehicle Speed",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(bottom = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    AnimatedContent(
                        targetState = telemetry.speedKmh,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "SpeedTextAnimation"
                    ) { targetSpeed ->
                        Text(
                            text = String.format(Locale.US, "%.1f", targetSpeed),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Clean Production Status Text
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = telemetry.sensorHealth,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
