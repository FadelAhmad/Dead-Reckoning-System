package com.example.deadreckoningsystem.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.ui.theme.AmberWarning
import com.example.deadreckoningsystem.ui.theme.NavySurface
import com.example.deadreckoningsystem.ui.theme.NeonCyan
import com.example.deadreckoningsystem.ui.theme.NeonGreen
import com.example.deadreckoningsystem.ui.theme.RedOutage
import com.example.deadreckoningsystem.ui.theme.SlateBorder
import com.example.deadreckoningsystem.ui.theme.SlateDarkBg
import com.example.deadreckoningsystem.ui.theme.TextPrimary

/**
 * Floating Bottom Control Panel containing interactive buttons for hackathon jury demos:
 * 1. "Simulate GPS Outage": Toggles between GNSS LOCKED and AI DEAD RECKONING.
 * 2. "Recalibrate IMU": Resets origin reference frame and clears accumulation drift.
 */
@Composable
fun BottomControlPanel(
    navState: NavState,
    onToggleGpsOutage: () -> Unit,
    onRecalibrateImu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOutageActive = navState == NavState.AI_DEAD_RECKONING

    val outageButtonBg by animateColorAsState(
        targetValue = if (isOutageActive) RedOutage.copy(alpha = 0.25f) else AmberWarning.copy(alpha = 0.15f),
        label = "OutageButtonBg"
    )

    val outageButtonBorder by animateColorAsState(
        targetValue = if (isOutageActive) RedOutage else AmberWarning,
        label = "OutageButtonBorder"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .border(1.dp, SlateBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = NavySurface.copy(alpha = 0.94f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "HACKATHON DEMO & FAILOVER CONTROLS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = NeonCyan
                ),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Simulate GPS Outage Toggle
                OutlinedButton(
                    onClick = onToggleGpsOutage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = outageButtonBg,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, outageButtonBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isOutageActive) Icons.Default.SatelliteAlt else Icons.Default.GpsOff,
                            contentDescription = "Toggle Outage",
                            tint = if (isOutageActive) NeonGreen else AmberWarning
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOutageActive) "Restore GPS" else "Simulate GPS Outage",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Button 2: Recalibrate IMU
                OutlinedButton(
                    onClick = onRecalibrateImu,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SlateDarkBg.copy(alpha = 0.6f),
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recalibrate IMU",
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recalibrate",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
