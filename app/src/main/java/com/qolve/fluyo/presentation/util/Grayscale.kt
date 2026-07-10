package com.qolve.fluyo.presentation.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Renders the element in grayscale when [enabled]. Used for locked badges, where the
 * emoji glyph ignores tint/color treatments and alpha alone doesn't read as "not earned".
 * saveLayer + saturation(0) works on every supported API level (RenderEffect needs 31+).
 */
fun Modifier.grayscale(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return drawWithCache {
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        onDrawWithContent {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                drawContent()
                canvas.restore()
            }
        }
    }
}
