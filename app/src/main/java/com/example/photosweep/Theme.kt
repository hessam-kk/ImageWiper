package com.example.photosweep

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D1A0F),
    onPrimaryContainer = Color(0xFFFFDBD1),

    secondary = Color(0xFFE0E0E0),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFE0E0E0),

    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1A3D1C),
    onTertiaryContainer = Color(0xFFB8F0BA),

    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFF9E9E9E),

    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF3D1A1A),
    onErrorContainer = Color(0xFFFFB4AB),

    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
)

@Composable
fun PhotoSweepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
