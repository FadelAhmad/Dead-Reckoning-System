package com.example.deadreckoningsystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.deadreckoningsystem.ui.NavigationScreen
import com.example.deadreckoningsystem.ui.OpeningScreen
import com.example.deadreckoningsystem.ui.theme.DeadReckoningTheme
import com.example.deadreckoningsystem.ui.theme.LogoCanvasBg
import com.example.deadreckoningsystem.ui.theme.SlateDarkBg

class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions updated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable Immersive Fullscreen Mode (Hides status bar & navigation home bar)
        hideSystemBars()

        setContent {
            DeadReckoningTheme {
                var showOpeningScreen by rememberSaveable { mutableStateOf(true) }

                Crossfade(
                    targetState = showOpeningScreen,
                    label = "AppScreenTransition"
                ) { isOpening ->
                    if (isOpening) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = LogoCanvasBg
                        ) {
                            OpeningScreen(
                                onStartNavigation = { showOpeningScreen = false }
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = SlateDarkBg
                        ) {
                            NavigationScreen(
                                onShowOpeningScreen = { showOpeningScreen = true }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        requestLocationPermissionsIfNeeded()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun requestLocationPermissionsIfNeeded() {
        val fineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fineLocation != PackageManager.PERMISSION_GRANTED ||
            coarseLocation != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
