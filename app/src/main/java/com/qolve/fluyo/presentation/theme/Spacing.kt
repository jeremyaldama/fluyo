package com.qolve.fluyo.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale on a 4dp base. Mirrors the `--s-1 .. --s-11` tokens from the Fluyo CSS spec.
 *
 * Use named accessors (`FluyoSpacing.md`) rather than the raw step indices — they read better
 * at call sites and survive a future renumber.
 *
 * The scale is intentionally non-linear past `md`: cards and screen-edge gutters cluster
 * around 16–24 dp, hero-block separations around 32–48 dp. Picking from a fixed set keeps
 * the rhythm consistent across screens.
 */
object FluyoSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val huge: Dp = 48.dp
    val mega: Dp = 64.dp
    val ultra: Dp = 80.dp
}
