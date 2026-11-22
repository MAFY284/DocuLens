package com.doculens.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro")
}

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE5FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0B1B54),
    secondary = BrandTeal,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3F5EC),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00382C),
    tertiary = BrandCoral,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4A5165),
    outline = LightOutline,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE1E4EE),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color.White
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0A1B57),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF233E9C),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE5FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF5BE0BE),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00382C),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF00513F),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3F5EC),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFB4A1),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF5A1A0B),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB6BECD),
    outline = DarkOutline,
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF2A3142),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005)
)

private val DocuShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun DocuLensTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            window.decorView.setBackgroundColor(colors.background.toArgb())
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = DocuTypography,
        shapes = DocuShapes,
        content = content
    )
}
