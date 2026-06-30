package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.presentation.theme.AccentAmber
import com.qolve.fluyo.presentation.theme.CoralRamp500
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoTeal
// Switched off MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) — center labels read from MaterialTheme.colorScheme so the
// dark canvas doesn't swallow the "TE QUEDA", "S/" prefix, and "de S/ X" caption.
import com.qolve.fluyo.presentation.theme.TealRamp100
import com.qolve.fluyo.presentation.theme.TealRamp500
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.presentation.util.money
import androidx.compose.ui.res.stringResource
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hero budget ring. Signature elements per the Fluyo spec:
 *   • Animated arc fills like ink (700ms).
 *   • Brand coral dot sits at the *progress endpoint* — the same accent motif that appears
 *     beside the wordmark, on the FAB, and on badges.
 *   • Center splits "S/" (small grey) + integer (huge bold) + ".XX" (smaller grey
 *     superscript) — composed manually rather than as a single Text so the typographic
 *     hierarchy reads cleanly even on long amounts.
 *
 * When `breakdown.monthlyBudget == 0`, we render the empty-state variant: full teal ring,
 * "TIENES TODO" label, the budget amount in big, and a serif italic "para el mes —
 * disfrútalo." subtitle.
 *
 * @param size Outer diameter. Default 220dp matches the mockup's prominent hero.
 */
@Composable
fun BudgetCircle(
    breakdown: MonthlyBreakdown,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val percentage by animateFloatAsState(
        targetValue = breakdown.percentageUsed,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "budgetArc",
    )

    // Traffic-light arc (HU-06): green while healthy, amber as the budget tightens,
    // coral once most of it is spent. Keyed off the target percentage (not the animated
    // value) so the color is stable rather than sweeping through ramps during the fill.
    val arcColor = when {
        breakdown.percentageUsed < 0.5f -> TealRamp500
        breakdown.percentageUsed < 0.8f -> AccentAmber
        else -> CoralRamp500
    }
    val trackColor = TealRamp100
    val strokeWidthDp = 18.dp

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidthDp.toPx(), cap = StrokeCap.Round)
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            // Progress arc
            if (breakdown.monthlyBudget > 0.0) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * percentage,
                    useCenter = false,
                    style = stroke,
                )
                // Coral dot at progress endpoint — the recurring brand motif.
                val angleRad = Math.toRadians((-90f + 360f * percentage).toDouble())
                val radiusPx = (size.toPx() - strokeWidthDp.toPx()) / 2f
                val cx = this.size.width / 2f + (radiusPx * cos(angleRad)).toFloat()
                val cy = this.size.height / 2f + (radiusPx * sin(angleRad)).toFloat()
                drawCircle(
                    color = CoralRamp500,
                    radius = 7.dp.toPx(),
                    center = Offset(cx, cy),
                )
            }
        }

        if (breakdown.monthlyBudget <= 0.0) {
            // No budget yet — show a friendly "set a budget" prompt
            BudgetCenterEmpty()
        } else if (breakdown.totalSpent <= 0.0) {
            // Budget set, nothing spent yet — celebrate the full balance
            BudgetCenterFull(breakdown)
        } else {
            // Standard "te queda" display
            BudgetCenterRemaining(breakdown)
        }
    }
}

/**
 * Standard center: TE QUEDA / S/ 482.40 / de S/ 1,200
 */
@Composable
private fun BudgetCenterRemaining(breakdown: MonthlyBreakdown) {
    val remaining = breakdown.remaining.coerceAtLeast(0.0)
    val integer = remaining.toLong()
    val cents = ((remaining - integer) * 100).toLong().coerceIn(0, 99)
    val integerFormatted = java.text.NumberFormat.getNumberInstance(
        java.util.Locale.forLanguageTag("es-PE"),
    ).format(integer)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.home_balance_label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = currencySymbol(),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = integerFormatted,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.2).sp,
                ),
            )
            Text(
                text = ".%02d".format(cents),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "de " + money(breakdown.monthlyBudget),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * Full-balance center (first day of month, nothing spent yet).
 * "TIENES TODO / S/ 1,200.00 / para el mes — disfrútalo."
 */
@Composable
private fun BudgetCenterFull(breakdown: MonthlyBreakdown) {
    val integer = breakdown.monthlyBudget.toLong()
    val integerFormatted = java.text.NumberFormat.getNumberInstance(
        java.util.Locale.forLanguageTag("es-PE"),
    ).format(integer)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.home_balance_full_label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = currencySymbol(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = integerFormatted,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.2).sp,
                ),
            )
            Text(
                text = ".00",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_balance_full_subtitle),
            style = com.qolve.fluyo.presentation.theme.FluyoSpecialty.taglineSerif.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun BudgetCenterEmpty() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Define tu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = "presupuesto",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Suppress("unused")
private val previewColor: Color = FluyoTeal
@Suppress("unused")
private val previewCoral: Color = FluyoCoral
