package com.example.deadreckoningsystem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = SlateDarkBg,
    primaryContainer = NavySurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = NeonGreen,
    onSecondary = SlateDarkBg,
    tertiary = AmberWarning,
    background = SlateDarkBg,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder
)

@Composable
fun DeadReckoningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
