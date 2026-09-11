package com.example.deadreckoningsystem.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.deadreckoningsystem.R
import com.example.deadreckoningsystem.ui.components.BottomControlPanel
import com.example.deadreckoningsystem.ui.components.MapCanvas
import com.example.deadreckoningsystem.ui.components.NavigationHUD
import com.example.deadreckoningsystem.ui.theme.LogoCanvasBg
import com.example.deadreckoningsystem.viewmodel.NavigationViewModel

/**
 * Full-screen Jetpack Compose Navigation View integrating Google Maps vector tiles,
 * top status banner HUD, app logo quick-action button, and bottom manual GPS controls.
 */
@Composable
fun NavigationScreen(
    viewModel: NavigationViewModel = viewModel(),
    onShowOpeningScreen: (() -> Unit)? = null
) {
    val navState by viewModel.navState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetryState.collectAsStateWithLifecycle()
    val trajectoryHistory by viewModel.trajectoryHistory.collectAsStateWithLifecycle()
    val isManualGpsDisabled by viewModel.isManualGpsDisabled.collectAsStateWithLifecycle()
    val anchorLocation by viewModel.anchorLocation.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fullscreen Navigation Map with vector tiles & trajectory path
        MapCanvas(
            telemetry = telemetry,
            navState = navState,
            trajectoryHistory = trajectoryHistory,
            anchorLocation = anchorLocation,
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Banner HUD Overlay
        NavigationHUD(
            telemetry = telemetry,
            navState = navState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // Floating Logo Shortcut Button (returns to opening screen & info)
        if (onShowOpeningScreen != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 22.dp)
                    .size(46.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(LogoCanvasBg)
                    .border(1.5.dp, Color(0xFFD0D6DC), CircleShape)
                    .clickable { onShowOpeningScreen() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo & Overview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Floating Bottom Control Panel Overlay
        BottomControlPanel(
            isManualGpsDisabled = isManualGpsDisabled,
            onToggleUseGps = { viewModel.toggleUseGps() },
            onRecalibrateImu = { viewModel.recalibrateImu() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
