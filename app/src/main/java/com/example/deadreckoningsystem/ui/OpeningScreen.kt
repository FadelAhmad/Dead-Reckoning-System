package com.example.deadreckoningsystem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deadreckoningsystem.R
import com.example.deadreckoningsystem.ui.theme.LogoCanvasBg
import com.example.deadreckoningsystem.ui.theme.LogoCardBg
import com.example.deadreckoningsystem.ui.theme.LogoDeepTeal
import com.example.deadreckoningsystem.ui.theme.LogoSlateAccent
import kotlinx.coroutines.delay

/**
 * App Opening / Splash Screen featuring the custom Dead Reckoning Tunnel & Mountain Logo
 * placed on a seamlessly matching canvas (#EEEFF1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpeningScreen(
    onStartNavigation: () -> Unit
) {
    // Entrance animations
    val logoScale = remember { Animatable(0.85f) }
    val logoAlpha = remember { Animatable(0f) }
    var autoProgress by remember { mutableFloatStateOf(0f) }
    var isAutoNavPaused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(Unit) {
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = LinearEasing)
        )
    }

    // Auto-launch timer (5 seconds) with progress bar
    LaunchedEffect(isAutoNavPaused) {
        if (!isAutoNavPaused) {
            val totalSteps = 100
            val stepDelay = 45L // ~4.5 seconds total
            for (i in 0..totalSteps) {
                autoProgress = i / 100f
                delay(stepDelay)
            }
            onStartNavigation()
        }
    }

    // Gentle pulse animation for the navigation start button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val buttonPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonPulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LogoCanvasBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LogoCanvasBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(LogoCardBg)
                        .border(1.dp, Color(0xFFD3D8DE), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00B074))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% OFFLINE EDGE COMPUTING",
                        color = LogoDeepTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Center Area: Logo + Titles + Capability Badges
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Custom Mountain-Tunnel Navigation Logo on Matching Canvas
                    Box(
                        modifier = Modifier
                            .size(290.dp)
                            .scale(logoScale.value),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Dead Reckoning System Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // App Title
                    Text(
                        text = "DEAD RECKONING",
                        color = LogoDeepTeal,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle
                    Text(
                        text = "AI-Powered Tunnel & Blackout Navigation",
                        color = LogoSlateAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Capability Highlights
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CapabilityPill(icon = Icons.Default.Memory, text = "1D-CNN + Transformer")
                        Spacer(modifier = Modifier.width(6.dp))
                        CapabilityPill(icon = Icons.Default.Navigation, text = "9-State EKF")
                        Spacer(modifier = Modifier.width(6.dp))
                        CapabilityPill(icon = Icons.Default.Speed, text = "Zero-Drift ZUPT")
                        Spacer(modifier = Modifier.width(6.dp))
                        CapabilityPill(icon = Icons.Default.Sensors, text = "Stage 7 Recalibration")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom CTA Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Big Start Navigation Button
                    Button(
                        onClick = onStartNavigation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .scale(buttonPulse)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LogoDeepTeal,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Start",
                                tint = Color(0xFF00F5D4),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ENTER NAVIGATION",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-transition progress bar and indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isAutoNavPaused) "Auto-launch paused (tap button to enter)" else "Auto-launching navigation...",
                            color = Color(0xFF7A8E98),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                        if (!isAutoNavPaused) {
                            Text(
                                text = "${((1f - autoProgress) * 4.5f).toInt() + 1}s",
                                color = LogoDeepTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { autoProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = LogoDeepTeal,
                        trackColor = LogoCardBg
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Real-time IMU & GNSS Fusion • Zero Remote Dependencies",
                        color = Color(0xFF8C9BA3),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityPill(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LogoCardBg)
            .border(1.dp, Color(0xFFD7DCE1), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LogoDeepTeal,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = LogoDeepTeal,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
