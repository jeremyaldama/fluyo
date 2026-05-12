package com.qolve.fluyo.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = FluyoTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF002420),
    secondary = FluyoCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2),
    onSecondaryContainer = Color(0xFF001F23),
    tertiary = FluyoCoral,
    onTertiary = Color.White,
    tertiaryContainer = FluyoCoralLight,
    onTertiaryContainer = Color(0xFF3D1100),
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = Color.White,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = FluyoTealLight,
    onPrimary = Color(0xFF003733),
    primaryContainer = FluyoTealDark,
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF80DEEA),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFB2EBF2),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF5C1900),
    tertiaryContainer = Color(0xFF7E2B0F),
    onTertiaryContainer = FluyoCoralLight,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = Color(0xFF181D1C),
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun FluyoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic color intentionally disabled for brand consistency
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FluyoTypography,
        content = content,
    )
}
