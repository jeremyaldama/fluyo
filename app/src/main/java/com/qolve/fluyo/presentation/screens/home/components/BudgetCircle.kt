package com.qolve.fluyo.presentation.screens.home.components

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
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.presentation.theme.ErrorRed
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.SuccessGreen
import com.qolve.fluyo.presentation.util.formatPen
import androidx.compose.ui.res.stringResource

@Composable
fun BudgetCircle(
    breakdown: MonthlyBreakdown,
    modifier: Modifier = Modifier,
) {
    val percentage by animateFloatAsState(
        targetValue = breakdown.percentageUsed,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "budgetArc",
    )

    val arcColor = when {
        breakdown.isOverBudget -> ErrorRed
        percentage >= 0.8f -> FluyoCoral
        percentage >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> SuccessGreen
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            // Progress arc
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * percentage,
                useCenter = false,
                style = stroke,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_remaining_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val remainingText = if (breakdown.isOverBudget) {
                "-" + formatPen(-breakdown.remaining)
            } else {
                formatPen(breakdown.remaining.coerceAtLeast(0.0))
            }
            Text(
                text = remainingText,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (breakdown.isOverBudget) ErrorRed else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.home_of_budget,
                    formatPen(breakdown.monthlyBudget),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("unused")
private val previewSwatch = FluyoTeal to Color.Transparent
