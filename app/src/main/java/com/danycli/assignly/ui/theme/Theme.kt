package com.danycli.assignmentchecker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Cyprus,
    onPrimary = White,
    primaryContainer = LightSand,
    onPrimaryContainer = Cyprus,
    secondary = Sand,
    onSecondary = Cyprus,
    background = Sand,
    onBackground = Cyprus,
    surface = White,
    onSurface = Cyprus,
    error = Color.Red,
    onError = White
)

private val DarkColorScheme = LightColorScheme

@Composable
fun AssignmentCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme // Use light theme as requested for specific colors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
