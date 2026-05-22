package com.danycli.assignmentchecker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.danycli.assignmentchecker.ThemeMode

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

private val DarkColorScheme = darkColorScheme(
    primary = CyprusLight,
    onPrimary = White,
    primaryContainer = CyprusDark,
    onPrimaryContainer = White,
    secondary = CyprusDark,
    onSecondary = White,
    background = Color(0xFF101418),
    onBackground = Color(0xFFE5EAF0),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE5EAF0),
    error = Color(0xFFCF6679),
    onError = Black
)

@Composable
fun AssignmentCheckerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
