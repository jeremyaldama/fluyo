package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.presentation.util.formatWholeAmount
import com.qolve.fluyo.presentation.theme.AccentLime
import com.qolve.fluyo.presentation.theme.AccentRose
// Direct neutral ramp refs removed in favor of colorScheme.* for dark-mode flip.
import com.qolve.fluyo.presentation.theme.TealRamp500

/**
 * Three-card horizontal strip that sits between the budget ring and the Movimientos list
 * on Home. Each card has the same shape: a small caps eyebrow, a large value (sometimes
 * split into prefix + magnitude + suffix), and an optional small modifier line below.
 *
 * Per the mockup:
 *   • "ESTA SEMANA" — week-to-date spend + week-over-week change (negative is good = green).
 *   • "PROMEDIO" — average daily spend in the current month.
 *   • "PRÓX. COBRO" — days until the next paycheck. We don't model salaries yet, so the
 *     card shows "—" until that data exists.
 *
 * @param weekSpend Total spend in the last 7 days.
 * @param weekDeltaPct Percent change vs the previous 7-day window. Null = no baseline.
 * @param dailyAvg Mean daily spend so far this month.
 * @param daysUntilNextPay Days until the next paycheck, or null when unknown.
 */
@Composable
fun StatsStrip(
    weekSpend: MoneyAmount,
    weekDeltaPct: Float?,
    dailyAvg: MoneyAmount,
    daysUntilNextPay: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            label = stringResource(R.string.home_stat_week),
            modifier = Modifier.weight(1f),
        ) {
            BigMoney(weekSpend)
            if (weekDeltaPct != null) {
                DeltaChip(deltaPct = weekDeltaPct)
            }
        }
        StatCard(
            label = stringResource(R.string.home_stat_avg),
            modifier = Modifier.weight(1f),
        ) {
            BigMoney(dailyAvg)
            Text(
                text = stringResource(R.string.home_stat_per_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        // Hidden while there is no salary model feeding it — a permanent "—" reads as
        // broken. HomeScreen still passes null; the card returns when the data exists.
        if (daysUntilNextPay != null) {
            StatCard(
                label = stringResource(R.string.home_stat_next_pay),
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = daysUntilNextPay.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        text = "d",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.9.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** "S/" prefix + integer amount. Used in both ESTA SEMANA and PROMEDIO. */
@Composable
private fun BigMoney(value: MoneyAmount) {
    val formatted = formatWholeAmount(value)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = currencySymbol(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Spacer(Modifier.padding(1.dp))
        Text(
            text = formatted,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Tiny percent-change chip. Negative spending change = green (saving). Positive = coral
 * (spending more). Threshold 0.5% to avoid showing noise like "+0%".
 */
@Composable
private fun DeltaChip(deltaPct: Float) {
    val isUnder = deltaPct < -0.5f
    val isOver = deltaPct > 0.5f
    val (text, color) = when {
        isUnder -> stringResource(R.string.home_stat_change_neg, kotlin.math.abs(deltaPct).toInt()) to AccentLime
        isOver -> stringResource(R.string.home_stat_change_pos, deltaPct.toInt()) to AccentRose
        else -> "—" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
    )
}

@Suppress("unused")
private val previewMarker: Color = TealRamp500
