package com.qolve.fluyo.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fluyo corner-radius rhythm (`--r-xs .. --r-pill` in the CSS spec).
 *
 * Used for:
 *   • Tier 1 (raw `Dp`) — when a screen needs a specific radius value (e.g. `RoundedCornerShape(FluyoRadii.lg)`).
 *   • Tier 2 (Material `Shapes`) — wired into `MaterialTheme.shapes` so components like
 *     `Card` and `Surface` pick up the rhythm automatically.
 *
 * **Pill.** Use `RoundedCornerShape(FluyoRadii.pill)` (or `CircleShape`) for buttons, chips,
 * and the FAB. Setting `pill = 999.dp` is a sentinel — Compose clamps it to the smaller
 * dimension so any rectangular component becomes fully rounded.
 */
object FluyoRadii {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 18.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 40.dp
    val pill: Dp = 999.dp
}

/**
 * Material 3 [Shapes] mapping. Compose component defaults flow from these:
 *   • Cards default to `shapes.medium`
 *   • Dialogs / modal sheets default to `shapes.extraLarge`
 *   • Buttons default to `shapes.full` (rounded pill)
 */
val FluyoShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(FluyoRadii.xs),
    small = RoundedCornerShape(FluyoRadii.sm),
    medium = RoundedCornerShape(FluyoRadii.md),
    large = RoundedCornerShape(FluyoRadii.lg),
    extraLarge = RoundedCornerShape(FluyoRadii.xl),
)
