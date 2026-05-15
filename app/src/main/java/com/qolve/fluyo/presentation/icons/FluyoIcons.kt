package com.qolve.fluyo.presentation.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.presentation.theme.CoralRamp500

/**
 * The Fluyo icon set — bar + slash + dot grammar.
 *
 * **Design principle.** Every glyph is built from three primitives:
 *   • thin black/ink outline (1.6dp stroke at 24dp canvas)
 *   • optional inner detail (slash or short bar)
 *   • a small coral *accent dot* placed at a meaningful corner of the glyph
 *
 * The accent dot is baked into the path so the same icon renders identically wherever
 * it's used — there's no separate overlay composable to wire up. The dot is filled in
 * `CoralRamp500`; the outline takes its tint from `LocalContentColor`, so selected and
 * unselected nav states get the right ink while the coral pin stays constant.
 *
 * **Canvas.** 24×24 dp, viewport 24×24. This matches Material's icon system so they
 * drop into `Icon(imageVector = ..., ...)` without modification.
 */
object FluyoIcons {

    /**
     * **Outline-only** variants for the bottom nav.
     *
     * Material's `Icon` applies a single `LocalContentColor` tint to the whole ImageVector,
     * which would also recolor the coral accent dot. To keep the dot brand-coral in both
     * light and dark mode, the nav bar renders the [Outline] variant (which gets tinted by
     * Material) and overlays a separate `BrandDot` at the appropriate corner.
     *
     * Non-nav surfaces (e.g. AddExpenseSheet tiles, Profile badges) can keep using the
     * full multi-color icons below since they render via `Image` without a tint and don't
     * need to flip on dark mode.
     */
    object Outline {
        // Strokes are painted in solid black so they form opaque pixels for the rasterizer.
        // Material's `Icon` then re-tints those pixels via `ColorFilter.tint(LocalContentColor)`
        // with SrcIn blend mode — black is just a placeholder; the visible color is whatever
        // the theme supplies (onSurface for unselected, primary for selected). Using
        // `Color.Unspecified` here breaks: the renderer treats it as "no color", paints
        // nothing, and the tint has no pixels to recolor → invisible icons in dark mode.
        val Home: ImageVector = lazyIcon("FluyoHomeOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 11f)
                lineTo(12f, 4f)
                lineTo(20f, 11f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()
            }
        }

        val Stats: ImageVector = lazyIcon("FluyoStatsOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5f, 19f); lineTo(5f, 14f)
                moveTo(11f, 19f); lineTo(11f, 11f)
                moveTo(17f, 19f); lineTo(17f, 7f)
            }
        }

        val Goals: ImageVector = lazyIcon("FluyoGoalsOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
            ) {
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.001f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 20f, 12f)
                close()
                moveTo(16.5f, 12f)
                arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.501f, 12f)
                arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.5f, 12f)
                close()
            }
            // Bullseye center — also painted in black so the tint can recolor it. Without a
            // filled center the Goals glyph reads as two empty rings instead of a target.
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.8f, 12f)
                arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.2f, 12f)
                arcTo(1.8f, 1.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 13.8f, 12f)
                close()
            }
        }

        val Profile: ImageVector = lazyIcon("FluyoProfileOutline") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15.2f, 8f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.801f, 8f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.2f, 8f)
                close()
                moveTo(5f, 20f)
                quadTo(12f, 13f, 19f, 20f)
            }
        }
    }

    /** Home — pitched-roof house outline with a coral dot inside the lower-right corner. */
    val Home: ImageVector = lazyIcon("FluyoHome") {
        // Outline: roof + walls. Drawn as a single open path.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Roof apex at (12, 4) -> down to (20, 11) right wall, (20, 19) bottom-right
            moveTo(4f, 11f)
            lineTo(12f, 4f)
            lineTo(20f, 11f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
        }
        // Accent dot at the lower-right interior of the house.
        coralDot(cx = 16.5f, cy = 16.5f, r = 1.1f)
    }

    /** Stats — three rising bars; tallest bar gets the coral dot at its tip. */
    val Stats: ImageVector = lazyIcon("FluyoStats") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // bar 1 (short)
            moveTo(5f, 19f); lineTo(5f, 14f)
            // bar 2 (medium)
            moveTo(11f, 19f); lineTo(11f, 11f)
            // bar 3 (tall)
            moveTo(17f, 19f); lineTo(17f, 7f)
        }
        coralDot(cx = 18.3f, cy = 6f, r = 1.1f)
    }

    /** Goals — concentric bullseye, coral dot at center. */
    val Goals: ImageVector = lazyIcon("FluyoGoals") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
        ) {
            // outer ring
            moveTo(20f, 12f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.001f, 12f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 20f, 12f)
            close()
            // middle ring
            moveTo(16.5f, 12f)
            arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.501f, 12f)
            arcTo(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.5f, 12f)
            close()
        }
        // Center bullseye dot — fills the inner circle with coral.
        coralDot(cx = 12f, cy = 12f, r = 1.8f)
    }

    /** Profile — head + shoulders outline with a coral dot on the upper-right shoulder. */
    val Profile: ImageVector = lazyIcon("FluyoProfile") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // head — circle at (12, 8) r=3.2
            moveTo(15.2f, 8f)
            arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.801f, 8f)
            arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.2f, 8f)
            close()
            // shoulders — flat arc from (5, 20) curving up
            moveTo(5f, 20f)
            quadTo(12f, 13f, 19f, 20f)
        }
        // Accent dot near the right side of the head (top-right).
        coralDot(cx = 16f, cy = 5.6f, r = 1.1f)
    }

    /** Manual entry — pencil with a coral dot on the eraser nub. */
    val Manual: ImageVector = lazyIcon("FluyoManual") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // pencil body diagonal
            moveTo(6f, 18f); lineTo(16f, 8f)
            // tip
            moveTo(16f, 8f); lineTo(18f, 6f); lineTo(20f, 8f); lineTo(18f, 10f); close()
        }
        coralDot(cx = 19.6f, cy = 5.6f, r = 1f)
    }

    /** Scan — bracketed viewfinder with a coral slash inside, matching the brand mark. */
    val Scan: ImageVector = lazyIcon("FluyoScan") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // top-left corner
            moveTo(4f, 8f); lineTo(4f, 4f); lineTo(8f, 4f)
            // top-right corner
            moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 8f)
            // bottom-right corner
            moveTo(20f, 16f); lineTo(20f, 20f); lineTo(16f, 20f)
            // bottom-left corner
            moveTo(8f, 20f); lineTo(4f, 20f); lineTo(4f, 16f)
        }
        // The slash inside the viewfinder is the coral element.
        path(
            stroke = SolidColor(CoralRamp500),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(7f, 12f); lineTo(17f, 12f)
        }
    }

    /** Voice — microphone with a coral dot at the top-right of the capsule. */
    val Voice: ImageVector = lazyIcon("FluyoVoice") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // capsule
            moveTo(15f, 6f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 6f)
            lineTo(9f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 12f)
            close()
            // stand
            moveTo(6f, 12f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18f, 12f)
            // stem
            moveTo(12f, 18f); lineTo(12f, 21f)
        }
        coralDot(cx = 15.6f, cy = 6.4f, r = 1f)
    }

    /** Add — plus sign with coral dot on the top-right of the cross. */
    val Add: ImageVector = lazyIcon("FluyoAdd") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
        coralDot(cx = 17.5f, cy = 6.5f, r = 1f)
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

/**
 * Lazy builder so each icon's path is constructed once. The receiver scope lets us call
 * `path { … }` and the [coralDot] helper, just like Material's own `materialIcon`
 * factories — but without depending on internal Material APIs.
 */
private inline fun lazyIcon(name: String, builder: ImageVector.Builder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(builder).build()
}

/**
 * Fills a small coral circle as an accent. Implemented as a square path with rounded
 * corners — `ImageVector.Builder` doesn't have a native `circle()` primitive, so we
 * approximate with four 90° arcs starting at (cx + r, cy).
 */
private fun ImageVector.Builder.coralDot(cx: Float, cy: Float, r: Float) {
    path(
        fill = SolidColor(CoralRamp500),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx, cy + r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx, cy - r)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx + r, cy)
        close()
    }
}
