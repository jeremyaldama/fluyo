package com.qolve.fluyo.presentation.screens.stats.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class DonutSlice(val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val totalRaw = slices.sumOf { it.value.toDouble() }.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalRaw > 0f) 1f else 0f,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "donutSweep",
    )

    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 26.dp.toPx()
            val gapDeg = if (slices.size > 1) 2.5f else 0f
            val trackColor = Color(0x1A000000)

            // Background ring
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )

            if (totalRaw <= 0f) return@Canvas

            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / totalRaw) * 360f * animatedProgress
                val sweepWithGap = (sweep - gapDeg).coerceAtLeast(0.5f)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweepWithGap,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = centerValue,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
