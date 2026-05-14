@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.qolve.fluyo.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R

/**
 * Fluyo type system.
 *
 * Three families:
 *   • `FluyoSans` — Hanken Grotesk, variable weight axis. UI + body. Default.
 *   • `FluyoSerif` — Instrument Serif italic. Delight moments (taglines, hero copy).
 *   • `FluyoMono` — JetBrains Mono. Phone numbers, timestamps, anything that should align
 *     character-for-character (tabular figures).
 *
 * **Variable weight.** Hanken Grotesk ships with a `wght` axis from 100 to 900. Each Font
 * binding below pins a target weight via `FontVariation.weight(...)` so Compose's font
 * matcher can pick the closest variation at runtime. For the "money number 600 → 800 → 700
 * pulse" animation we'll instantiate a separate `TextStyle` with an animatable
 * `FontVariation.Settings` at the call site — that's a runtime concern, not a token.
 *
 * **Letter-spacing.** Negative tracking on display + headline ranges mirrors the OKLCH-era
 * CSS spec (-0.04em … -0.005em). At Compose's metric sp this maps to roughly -0.5sp per 14sp
 * — close enough for the eye.
 */
val FluyoSans: FontFamily = FontFamily(
    Font(R.font.hanken_grotesk_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.hanken_grotesk_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.hanken_grotesk_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.hanken_grotesk_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.hanken_grotesk_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

val FluyoSerif: FontFamily = FontFamily(
    // Instrument Serif italic is single-weight, single-style — we use it only for short
    // accent text. Compose will scale and render the italic glyphs correctly.
    Font(R.font.instrument_serif_italic, FontWeight.Normal, style = FontStyle.Italic),
)

val FluyoMono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/**
 * Material 3 [Typography] mapped onto our type scale.
 *
 * Scale (per Fluyo spec):
 *   • display  56sp / 600 / -0.04em
 *   • headline 32sp / 600 / -0.025em
 *   • title    22sp / 600 / -0.015em
 *   • body-lg  17sp / 400 / -0.005em
 *   • body     15sp / 400
 *   • body-sm  13sp / 400 / 0.005em
 *   • label    13sp / 600 / 0.01em
 *   • caption  11sp / 600 / 0.08em uppercase  (apply text-transform at call site)
 *
 * Material's slot names don't perfectly mirror this scale — we map them as closely as
 * possible so existing code that reads `MaterialTheme.typography.titleMedium` etc. still
 * looks right.
 */
val FluyoTypography = Typography(
    // display
    displayLarge = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-2.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.2).sp,
    ),
    // headline
    headlineLarge = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    // title
    titleLarge = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // body
    bodyLarge = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.08.sp,
    ),
    // label
    labelLarge = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    // labelSmall is our "caption" — uppercase by convention at call site
    labelSmall = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.9.sp,
    ),
)

/**
 * Specialty styles for the small set of places that should use serif italic or mono.
 * These aren't part of Material's typography slots — pass them explicitly via
 * `style = FluyoSpecialty.taglineSerif`.
 */
object FluyoSpecialty {
    /** Instrument Serif italic. Use for taglines and hero accent copy only. */
    val taglineSerif: TextStyle = TextStyle(
        fontFamily = FluyoSerif,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )

    /** JetBrains Mono for phone numbers, timestamps, tabular numerics. */
    val mono: TextStyle = TextStyle(
        fontFamily = FluyoMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )

    /** Money — sans, tabular figures, slight negative tracking. The variable-weight pulse
     *  animation builds on top of this style at the call site. */
    val money: TextStyle = TextStyle(
        fontFamily = FluyoSans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
    )
}
