package com.qolve.fluyo.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Fluyo motion tokens. Durations and easings mirror the CSS spec.
 *
 *   • `EaseOut` — Material's expressive-decel curve. Use for entering elements,
 *     ring fills, content reveals.
 *   • `EaseInOut` — symmetric, for cross-fades and mode swaps.
 *   • `EaseSpring` — slight overshoot. Use for delight moments (badge unlock pop, FAB tap).
 *
 * Durations:
 *   • `Fast` (160ms) — tap feedback, small swaps
 *   • `Base` (260ms) — default transitions
 *   • `Slow` (480ms) — coordinated multi-element transitions
 *   • `Xslow` (900ms) — the signature "ink-fill" ring animation
 */
object FluyoMotion {
    val EaseOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    const val Fast: Int = 160
    const val Base: Int = 260
    const val Slow: Int = 480
    const val Xslow: Int = 900

    /**
     * Reusable spring spec for delight moments — pop animations on badge unlock, goal
     * completion, FAB tap. Tuned to overshoot ~8% then settle.
     */
    fun <T> popSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
