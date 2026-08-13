package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PhantomDarkColorScheme = darkColorScheme(
    primary = PhantomPrimary,
    onPrimary = PhantomOnPrimary,
    secondary = PhantomSecondary,
    tertiary = PhantomTertiary,
    background = PhantomBackground,
    onBackground = PhantomOnBackground,
    surface = PhantomSurface,
    onSurface = PhantomOnSurface,
    surfaceVariant = PhantomSurfaceVariant,
    onSurfaceVariant = PhantomOnSurfaceVariant,
    outline = PhantomOutline,
    error = PhantomError
)

@Composable
fun PhantomTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = PhantomDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.hashCode()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.hashCode()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
