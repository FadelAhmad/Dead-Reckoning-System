package com.example.deadreckoningsystem.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deadreckoningsystem.ui.theme.AmberWarning
import com.example.deadreckoningsystem.ui.theme.NavySurface
import com.example.deadreckoningsystem.ui.theme.NeonGreen
import com.example.deadreckoningsystem.ui.theme.SlateBorder
import com.example.deadreckoningsystem.ui.theme.SlateDarkBg
import com.example.deadreckoningsystem.ui.theme.TextMuted
import com.example.deadreckoningsystem.ui.theme.TextPrimary

/**
 * Bottom Control Panel containing the manual "Use GPS" toggle button.
 */
@Composable
fun BottomControlPanel(
    isManualGpsDisabled: Boolean,
    onToggleUseGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    // If NOT disabled manually, the user WANTS to use GPS.
    val isGpsActive = !isManualGpsDisabled

    val buttonBg by animateColorAsState(
        targetValue = if (isGpsActive) NeonGreen.copy(alpha = 0.22f) else SlateDarkBg.copy(alpha = 0.7f),
        label = "GpsButtonBg"
    )

    val buttonBorder by animateColorAsState(
        targetValue = if (isGpsActive) NeonGreen else SlateBorder,
        label = "GpsButtonBorder"
    )

    val headerText = if (isGpsActive) "GPS ACTIVE" else "GPS INACTIVE"
    val headerColor = if (isGpsActive) NeonGreen else AmberWarning

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
                text = headerText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = headerColor
                ),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Use GPS" Toggle Button
                OutlinedButton(
                    onClick = onToggleUseGps,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = buttonBg,
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, buttonBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isGpsActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = "Toggle Use GPS",
                            tint = if (isGpsActive) NeonGreen else TextMuted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isGpsActive) "USE GPS: ON" else "USE GPS: OFF",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isGpsActive) NeonGreen else TextMuted
                            )
                        )
                    }
                }
            }
        }
    }
}
