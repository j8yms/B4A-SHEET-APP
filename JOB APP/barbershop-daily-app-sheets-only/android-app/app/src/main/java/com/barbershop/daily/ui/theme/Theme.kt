package com.barbershop.daily.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00E5FF), // Neon Cyan
    secondary = Color(0xFF39FF14), // Neon Green
    tertiary = Color(0xFFD81B60), // Vibrant Pink
    background = Color(0xFF121212), // Dark Background
    surface = Color(0xFF1E1E1E), // Slightly lighter surface
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFFF1744)
)

private val LightColors = DarkColors // Force Dark Mode as per user request for "cool" look

@Composable
fun BarbershopTheme(
    darkTheme: Boolean = true, // Default to true
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}

