package com.example.deadreckoningsystem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.deadreckoningsystem.ui.components.BottomControlPanel
import com.example.deadreckoningsystem.ui.components.MapCanvas
import com.example.deadreckoningsystem.ui.components.NavigationHUD
import com.example.deadreckoningsystem.viewmodel.NavigationViewModel

/**
 * Full-screen Jetpack Compose Navigation View integrating Google Maps vector tiles,
 * top status banner HUD, and bottom manual GPS controls.
 */
@Composable
fun NavigationScreen(
    viewModel: NavigationViewModel = viewModel()
) {
    val navState by viewModel.navState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetryState.collectAsStateWithLifecycle()
    val trajectoryHistory by viewModel.trajectoryHistory.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fullscreen Navigation Map with vector tiles & trajectory path
        MapCanvas(
            telemetry = telemetry,
            navState = navState,
            trajectoryHistory = trajectoryHistory,
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

        // Floating Bottom Control Panel Overlay
        BottomControlPanel(
            navState = navState,
            onToggleUseGps = { viewModel.toggleUseGps() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
