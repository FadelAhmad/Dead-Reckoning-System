package com.example.deadreckoningsystem.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deadreckoningsystem.model.NavState
import com.example.deadreckoningsystem.model.VehicleTelemetry
import com.example.deadreckoningsystem.ui.theme.NeonCyan
import com.example.deadreckoningsystem.ui.theme.NeonGreen
import com.example.deadreckoningsystem.ui.theme.TextMuted
import com.example.deadreckoningsystem.ui.theme.TrajectoryTrailDr
import com.example.deadreckoningsystem.ui.theme.TrajectoryTrailGnss
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlin.math.cos

/**
 * Modern Google Maps Navigation View displaying live vector map geometry,
 * custom vehicle marker cursor, breadcrumb trajectory path, and DR status overlays.
 */
@Composable
fun MapCanvas(
    telemetry: VehicleTelemetry,
    navState: NavState,
    trajectoryHistory: List<Pair<Float, Float>>,
    anchorLocation: Pair<Double, Double>? = null,
    modifier: Modifier = Modifier
) {
    val vehicleLatLng = remember(telemetry.latitude, telemetry.longitude) {
        LatLng(telemetry.latitude, telemetry.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(vehicleLatLng, 17.5f)
    }

    val markerState = rememberMarkerState(position = vehicleLatLng)

    // Non-blocking camera and marker position updates
    LaunchedEffect(vehicleLatLng) {
        markerState.position = vehicleLatLng
        cameraPositionState.move(CameraUpdateFactory.newLatLng(vehicleLatLng))
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Google Maps Base View
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            // Trajectory Polyline Path anchored to session origin
            if (trajectoryHistory.size > 1) {
                val anchor = anchorLocation ?: Pair(telemetry.latitude, telemetry.longitude)
                val polylinePoints = remember(trajectoryHistory, anchor) {
                    trajectoryHistory.map { (x, y) ->
                        enuMetersToLatLon(x.toDouble(), y.toDouble(), anchor.first, anchor.second)
                    }
                }

                Polyline(
                    points = polylinePoints,
                    color = if (navState == NavState.AI_DEAD_RECKONING) TrajectoryTrailDr else TrajectoryTrailGnss,
                    width = 12f
                )
            }

            // Custom Glowing Vehicle Marker Cursor
            MarkerComposable(
                state = markerState,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
            ) {
                VehicleMarker(
                    bearingDegrees = telemetry.bearingDegrees,
                    navState = navState,
                    size = 56.dp
                )
            }
        }

        // 2. Compass Overlay (Top Left below HUD)
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

        // 3. Map Frame Label (Bottom Left above control panel)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 120.dp, start = 16.dp)
        ) {
            Text(
                text = "GOOGLE MAPS VECTOR TILES  •  2D WGS-84 / ENU FRAME",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextMuted
            )
        }
    }
}

private fun enuMetersToLatLon(xMeters: Double, yMeters: Double, refLat: Double, refLon: Double): LatLng {
    val latRad = Math.toRadians(refLat)
    val lat = refLat + (yMeters / 111320.0) // Positive North
    val lon = refLon + (xMeters / (111320.0 * cos(latRad)))
    return LatLng(lat, lon)
}
