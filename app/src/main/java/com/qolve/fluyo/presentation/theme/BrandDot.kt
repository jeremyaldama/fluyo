package com.qolve.fluyo.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The signature Fluyo "accent dot" motif. Recurs across the app:
 *   • next to the F-mark wordmark (the dot in "F.")
 *   • at the trailing end of the budget ring as a small endpoint marker
 *   • beside the FAB to draw the eye
 *   • on unlocked badges as a "new" marker
 *
 * Default color is the brand accent (coral). Default size is 8 dp — small enough to read
 * as punctuation, not as a UI control.
 */
@Composable
fun BrandDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.tertiary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
