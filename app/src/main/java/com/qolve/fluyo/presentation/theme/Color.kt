package com.qolve.fluyo.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Fluyo color tokens.
 *
 * The canonical definitions are OKLCH ramps in our design system documentation. The values
 * here are their sRGB equivalents (computed with the OKLab → sRGB conversion), rounded to
 * 8-bit. Compose `Color` is sRGB-only, so any future OKLCH tweak needs to round-trip back
 * through that conversion.
 *
 * **Layering.**
 *   • Tier 1: raw ramps (`TealRamp500`, `NeutralRamp900`, …). Don't reference from screens —
 *     they're the typographic palette only.
 *   • Tier 2: semantic role names (`FluyoBrand`, `FluyoAccent`, `FluyoInk1`, …). Use these in
 *     theme glue.
 *   • Tier 3: Material 3 `ColorScheme` slots wired in [Theme.kt]. Most UI code should read
 *     `MaterialTheme.colorScheme.primary` etc., NOT these constants directly.
 *
 * Legacy aliases (`FluyoTeal`, `FluyoCoral`, etc.) are preserved at their old names but now
 * point to the corresponding ramp steps in the new system. New code should use the ramp
 * constants or, ideally, `MaterialTheme.colorScheme.*`.
 */

// ─── Teal ramp (primary, around #00897B) ────────────────────────────────────
val TealRamp50 = Color(0xFFE9FAF8)
val TealRamp100 = Color(0xFFCEF2EE)
val TealRamp200 = Color(0xFFA5E4DC)
val TealRamp300 = Color(0xFF74CFC3)
val TealRamp400 = Color(0xFF41B4A3)
val TealRamp500 = Color(0xFF169685)
val TealRamp600 = Color(0xFF0B7A6B)
val TealRamp700 = Color(0xFF005D51)
val TealRamp800 = Color(0xFF034239)
val TealRamp900 = Color(0xFF012A24)
val TealRamp950 = Color(0xFF001612)

// ─── Coral ramp (accent, around #FF7043) ────────────────────────────────────
val CoralRamp50 = Color(0xFFFFF2EC)
val CoralRamp100 = Color(0xFFFFE3D3)
val CoralRamp200 = Color(0xFFFFC5A7)
val CoralRamp300 = Color(0xFFFFA378)
val CoralRamp400 = Color(0xFFFF8958)
val CoralRamp500 = Color(0xFFFE773D)
val CoralRamp600 = Color(0xFFE15D2E)
val CoralRamp700 = Color(0xFFB54628)
val CoralRamp800 = Color(0xFF802F21)
val CoralRamp900 = Color(0xFF511D16)

// ─── Neutral ramp (warm-tinted greys) ───────────────────────────────────────
val NeutralRamp0 = Color(0xFFFFFFFF)
val NeutralRamp50 = Color(0xFFF7FBFA)
val NeutralRamp100 = Color(0xFFF1F6F5)
val NeutralRamp150 = Color(0xFFEAF1EF)
val NeutralRamp200 = Color(0xFFE0E8E6)
val NeutralRamp300 = Color(0xFFCCD7D4)
val NeutralRamp400 = Color(0xFFA5AFAC)
val NeutralRamp500 = Color(0xFF7A8482)
val NeutralRamp600 = Color(0xFF5D6563)
val NeutralRamp700 = Color(0xFF414846)
val NeutralRamp800 = Color(0xFF252D2B)
val NeutralRamp850 = Color(0xFF17201E)
val NeutralRamp900 = Color(0xFF0C1513)
val NeutralRamp950 = Color(0xFF030907)

// ─── Other semantic accents ─────────────────────────────────────────────────
val AccentLime = Color(0xFF75C678)
val AccentAmber = Color(0xFFECBB48)
val AccentRose = Color(0xFFEB5A67)
val AccentViolet = Color(0xFF8E71D6)
val AccentCobalt = Color(0xFF1380C7)

// ─── Dark-theme overrides (in OKLCH these had unique lightness levels) ──────
val DarkBorder = Color(0xFF202A28)
val DarkDivider = Color(0xFF17201E)
val DarkInk1 = Color(0xFFEFF5F3)
val DarkInk2 = Color(0xFFB1BCB9)
val DarkInk3 = Color(0xFF77817F)
val DarkBrandSoft = Color(0xFF003930)
val DarkAccentSoft = Color(0xFF59291B)

// ─── Legacy aliases — DO NOT remove; many screens reference them ────────────
// New code should prefer ramp constants or MaterialTheme.colorScheme.*
val FluyoTeal = TealRamp500
val FluyoTealLight = TealRamp300
val FluyoTealDark = TealRamp700
val FluyoCyan = AccentCobalt
val FluyoCoral = CoralRamp500
val FluyoCoralLight = CoralRamp300

// Light-mode surface aliases (kept for compatibility with existing imports)
val SurfaceLight = NeutralRamp50
val OnSurfaceLight = NeutralRamp900
val SurfaceVariantLight = NeutralRamp100
val OnSurfaceVariantLight = NeutralRamp700
val OutlineLight = NeutralRamp400

// Dark-mode surface aliases
val SurfaceDark = NeutralRamp950
val OnSurfaceDark = DarkInk1
val SurfaceVariantDark = NeutralRamp850
val OnSurfaceVariantDark = DarkInk2
val OutlineDark = DarkBorder

// Semantic colors (legacy names → new ramp targets)
val SuccessGreen = AccentLime
val WarningAmber = AccentAmber
val ErrorRed = AccentRose
