package com.qolve.fluyo.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Fluyo theme entry point.
 *
 * **Layering.** Color tokens in `Color.kt` are unopinionated ramps. This file wires them
 * onto Material 3's [androidx.compose.material3.ColorScheme] slots, which is what most UI
 * code consumes via `MaterialTheme.colorScheme.*`.
 *
 * **Light is canonical.** The design system is designed around the light palette; dark is a
 * faithful inversion. We default to following the system setting, but light is what the
 * marketing screenshots show.
 *
 * **Dynamic color stays off.** Android 12+ can derive a palette from the user's wallpaper,
 * but Fluyo is brand-led — teal must read as teal regardless of wallpaper. If a user wants
 * a non-teal accent in the future, that's a setting we expose ourselves (see the
 * `data-accent` token variants), not a wallpaper-derived guess.
 */
private val LightColors = lightColorScheme(
    // Brand — teal-500 in OKLCH terms
    primary = TealRamp500,
    onPrimary = NeutralRamp0,
    primaryContainer = TealRamp100,
    onPrimaryContainer = TealRamp800,
    inversePrimary = TealRamp300,

    // Secondary slot — we use it for muted teal surfaces (e.g. small badges)
    secondary = TealRamp600,
    onSecondary = NeutralRamp0,
    secondaryContainer = TealRamp50,
    onSecondaryContainer = TealRamp800,

    // Tertiary — coral accent
    tertiary = CoralRamp500,
    onTertiary = NeutralRamp0,
    tertiaryContainer = CoralRamp100,
    onTertiaryContainer = CoralRamp800,

    // Surfaces — neutrals with subtle teal tint
    background = NeutralRamp50,
    onBackground = NeutralRamp900,
    surface = NeutralRamp0,
    onSurface = NeutralRamp900,
    surfaceVariant = NeutralRamp100,
    onSurfaceVariant = NeutralRamp700,
    surfaceTint = TealRamp500,
    inverseSurface = NeutralRamp900,
    inverseOnSurface = NeutralRamp50,

    // Outline + dividers
    outline = NeutralRamp400,
    outlineVariant = NeutralRamp200,
    scrim = NeutralRamp950,

    // Status
    error = AccentRose,
    onError = NeutralRamp0,
    errorContainer = CoralRamp50,
    onErrorContainer = CoralRamp800,
)

private val DarkColors = darkColorScheme(
    // In dark mode the brand reads stronger when raised on the tonal scale
    primary = TealRamp300,
    onPrimary = TealRamp900,
    primaryContainer = DarkBrandSoft,
    onPrimaryContainer = TealRamp100,
    inversePrimary = TealRamp600,

    secondary = TealRamp400,
    onSecondary = TealRamp950,
    secondaryContainer = TealRamp900,
    onSecondaryContainer = TealRamp100,

    tertiary = CoralRamp400,
    onTertiary = CoralRamp900,
    tertiaryContainer = DarkAccentSoft,
    onTertiaryContainer = CoralRamp100,

    background = NeutralRamp950,
    onBackground = DarkInk1,
    surface = NeutralRamp900,
    onSurface = DarkInk1,
    surfaceVariant = NeutralRamp850,
    onSurfaceVariant = DarkInk2,
    surfaceTint = TealRamp300,
    inverseSurface = NeutralRamp50,
    inverseOnSurface = NeutralRamp900,

    outline = DarkBorder,
    outlineVariant = DarkDivider,
    scrim = NeutralRamp0.copy(alpha = 0.0f), // unused; keep slot wired

    error = AccentRose,
    onError = NeutralRamp950,
    errorContainer = CoralRamp800,
    onErrorContainer = CoralRamp100,
)

@Composable
fun FluyoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FluyoTypography,
        shapes = FluyoShapes,
        content = content,
    )
}
