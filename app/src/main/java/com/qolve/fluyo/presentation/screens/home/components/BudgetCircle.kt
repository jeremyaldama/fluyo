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
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.SuccessGreen
import com.qolve.fluyo.presentation.util.formatPen
import androidx.compose.ui.res.stringResource

@Composable
fun BudgetCircle(
    breakdown: MonthlyBreakdown,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 160.dp,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    captionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trackOverride: Color? = null,
) {
    val percentage by animateFloatAsState(
        targetValue = breakdown.percentageUsed,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "budgetArc",
    )

    // Semantic color ramp: green (cruising) → amber (close) → coral (warning) → coral (over).
    // Deliberately NOT pure red on over-budget — for college students tracking small expenses,
    // crimson reads as financial shaming. Coral keeps the warning visible without punishing.
    val arcColor = when {
        breakdown.isOverBudget -> FluyoCoral
        percentage >= 0.8f -> FluyoCoral
        percentage >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> SuccessGreen
    }
    val trackColor = trackOverride ?: MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * percentage,
                useCenter = false,
                style = stroke,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Lead with the positive: "Has registrado S/ 165". The over/under framing
            // lives in the chip below the ring, not buried inside this label.
            Text(
                text = stringResource(R.string.home_registered_label),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatPen(breakdown.totalSpent),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = valueColor,
            )
            if (breakdown.monthlyBudget > 0.0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.home_of_budget,
                        formatPen(breakdown.monthlyBudget),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = captionColor,
                )
            }
        }
    }
}

@Suppress("unused")
private val previewSwatch = FluyoTeal to Color.Transparent
