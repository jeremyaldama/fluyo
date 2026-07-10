package com.qolve.fluyo.presentation.screens.stats

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.CategorySummary
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.theme.AccentLime
import com.qolve.fluyo.presentation.theme.AccentRose
import com.qolve.fluyo.presentation.theme.CoralRamp500
// Neutral ramp imports retired — see ProfileScreen for the same migration rationale.
import com.qolve.fluyo.presentation.theme.TealRamp500
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.presentation.util.money
import com.qolve.fluyo.presentation.util.parseHexColor
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Insights screen — the redesigned "Stats" page.
 *
 * Layout (per the mockup pair 04 + 05):
 *   1. Big "Insights" title (bottom-nav tab is still labeled "Stats").
 *   2. Three-segment pill: Semana / Mes / Año.
 *   3. Green/coral *insight banner* with an emoji and a one-line vs.-previous-period
 *      framing — the friendliest version of the comparison.
 *   4. Big multi-slice donut, total amount + "vs. S/ X mes ant." centered inside.
 *   5. Per-category rows — full-width white pill cards, colored vertical bar on the left,
 *      a thin colored progress bar under the category name, amount + percent on the right.
 *   6. Patrón semanal card — seven weekday markers with a coral dot on the peak day and
 *      a caption telling the user which weekday hits them hardest.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // More breathing room from the status bar (was 12.dp) and a touch more space
        // between sections (was 16.dp) so the donut, bars and pattern card each feel
        // like distinct chapters rather than one wall.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { TitleRow() }

        item {
            PeriodPill(selected = state.period, onSelect = viewModel::selectPeriod)
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(strokeWidth = 3.dp) }
            }
        } else if (state.summaries.isEmpty()) {
            item { EmptyState() }
        } else {
            item { InsightBanner(state = state) }
            item { DonutHero(state = state) }

            items(state.summaries, key = { it.categoryId ?: it.name }) { summary ->
                CategoryBarRow(summary = summary, total = state.total)
            }

            if (state.weekdayPattern.any { it.average > 0 }) {
                item { WeeklyPatternCard(state = state) }
            }
        }
    }
}

// ─── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun TitleRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── Period pill ────────────────────────────────────────────────────────────
//
// Custom segmented control to match the mockup exactly: pale grey rounded pill with a
// white pill-shaped indicator behind the selected segment, bold label on selected.

@Composable
private fun PeriodPill(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    // surfaceVariant gives us a recessed pill in both modes (pale grey in light, dark
    // grey-with-teal-tint in dark) that reads clearly above the canvas.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PeriodTab(
            label = stringResource(R.string.stats_period_week),
            selected = selected == StatsPeriod.WEEK,
            onClick = { onSelect(StatsPeriod.WEEK) },
            modifier = Modifier.weight(1f),
        )
        PeriodTab(
            label = stringResource(R.string.stats_period_month),
            selected = selected == StatsPeriod.MONTH,
            onClick = { onSelect(StatsPeriod.MONTH) },
            modifier = Modifier.weight(1f),
        )
        PeriodTab(
            label = stringResource(R.string.stats_period_year),
            selected = selected == StatsPeriod.YEAR,
            onClick = { onSelect(StatsPeriod.YEAR) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeriodTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

// ─── Insight banner ─────────────────────────────────────────────────────────

/**
 * Three-tier insight banner.
 *
 *  1. **Delta** — when the user spent meaningfully more (≥5%) or less (≥5%) than the
 *     previous period, that's the headline. ¡Chévere! when down, gentle warn when up.
 *  2. **Concentration** — otherwise, if a single category owns ≥60% of the total,
 *     surface that fact. Far more actionable than "you spent about the same":
 *     "Comida lidera tus gastos · 97% de tu total · S/ 160.00".
 *  3. **Daily average** — fallback when spend is well-distributed and the delta is
 *     small. Anchors the user with a per-day number, which is easier to mentally
 *     compare against a daily budget than a period total.
 *
 * "No baseline" still wins when there is literally no prior data to compare against.
 *
 * The 5% threshold (was 0.5%) intentionally suppresses noisy near-flat deltas so the
 * dominant-category insight actually gets a chance to render in real-world usage.
 */
@Composable
private fun InsightBanner(state: StatsUiState) {
    val delta = state.deltaPct
    val isUnder = (delta ?: 0f) < -5f
    val isOver = (delta ?: 0f) > 5f

    val topCat = state.topCategory
    val topShare = topCat?.share(state.total) ?: 0f
    val isDominant = !isUnder && !isOver && topCat != null && topShare >= 0.60f

    // Day count for the daily-average branch. `state.daily` is a continuous series the
    // ViewModel builds for the current range, so its size = days elapsed in the period.
    val daysInPeriod = state.daily.size.coerceAtLeast(1)
    val dailyAverage = if (state.total > 0.0) state.total / daysInPeriod else 0.0

    // Pick the variant up front; everything below reads from it.
    val variant: InsightVariant = when {
        delta == null && state.total <= 0.0 -> InsightVariant.NoBaseline
        delta == null -> InsightVariant.NoBaseline
        isUnder -> InsightVariant.Under(delta!!.absoluteValue.toInt())
        isOver -> InsightVariant.Over(delta!!.toInt())
        isDominant -> InsightVariant.Dominant(
            name = topCat!!.name,
            pct = (topShare * 100f).toInt(),
            amount = topCat.total,
            color = parseHexColor(topCat.color),
        )
        else -> InsightVariant.DailyAverage(amount = dailyAverage, days = daysInPeriod)
    }

    val emoji = when (variant) {
        InsightVariant.NoBaseline -> "✨"
        is InsightVariant.Under -> "👏"
        is InsightVariant.Over -> "⚠️"
        is InsightVariant.Dominant -> "🎯"
        is InsightVariant.DailyAverage -> "📊"
    }

    // Tile palette per variant. The dominant variant uses the category's own color
    // (orange for Comida, blue for Transporte, etc.) to make the banner feel attached
    // to the row below it.
    val (background, foreground, accentBg) = when (variant) {
        is InsightVariant.Under -> Triple(
            AccentLime.copy(alpha = 0.24f),
            MaterialTheme.colorScheme.onSurface,
            AccentLime.copy(alpha = 0.38f),
        )
        is InsightVariant.Over -> Triple(
            CoralRamp500.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.onSurface,
            CoralRamp500.copy(alpha = 0.34f),
        )
        is InsightVariant.Dominant -> Triple(
            variant.color.copy(alpha = 0.16f),
            MaterialTheme.colorScheme.onSurface,
            variant.color.copy(alpha = 0.28f),
        )
        is InsightVariant.DailyAverage, InsightVariant.NoBaseline -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outlineVariant,
        )
    }

    val title: String = when (variant) {
        InsightVariant.NoBaseline -> stringResource(R.string.stats_insight_no_baseline)
        is InsightVariant.Under -> stringResource(R.string.stats_insight_under_pct, variant.pct)
        is InsightVariant.Over -> stringResource(R.string.stats_insight_over_pct, variant.pct)
        is InsightVariant.Dominant -> stringResource(R.string.stats_insight_dominant_title, variant.name)
        is InsightVariant.DailyAverage -> stringResource(
            R.string.stats_insight_avg_day_title,
            money(variant.amount),
        )
    }

    val caption: String = when (variant) {
        InsightVariant.NoBaseline -> ""
        is InsightVariant.Under -> stringResource(periodUnderCaption(state.period))
        is InsightVariant.Over -> stringResource(periodOverCaption(state.period))
        is InsightVariant.Dominant -> stringResource(
            R.string.stats_insight_dominant_caption,
            variant.pct,
            money(variant.amount),
        )
        is InsightVariant.DailyAverage -> stringResource(
            when (state.period) {
                StatsPeriod.WEEK -> R.string.stats_insight_avg_day_caption_week
                StatsPeriod.MONTH -> R.string.stats_insight_avg_day_caption_month
                StatsPeriod.YEAR -> R.string.stats_insight_avg_day_caption_year
            },
            variant.days,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accentBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foreground,
            )
            if (caption.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Sealed type drives both the copy and the tile palette for the insight banner.
 * Keeping the decision in one place avoids the "which color goes with which message"
 * skew the previous Triple-based logic invited.
 */
private sealed interface InsightVariant {
    data object NoBaseline : InsightVariant
    data class Under(val pct: Int) : InsightVariant
    data class Over(val pct: Int) : InsightVariant
    data class Dominant(val name: String, val pct: Int, val amount: Double, val color: Color) : InsightVariant
    data class DailyAverage(val amount: Double, val days: Int) : InsightVariant
}

private fun periodUnderCaption(period: StatsPeriod): Int = when (period) {
    StatsPeriod.WEEK -> R.string.stats_insight_under_caption_week
    StatsPeriod.MONTH -> R.string.stats_insight_under_caption_month
    StatsPeriod.YEAR -> R.string.stats_insight_under_caption_year
}

private fun periodOverCaption(period: StatsPeriod): Int = when (period) {
    StatsPeriod.WEEK -> R.string.stats_insight_over_caption_week
    StatsPeriod.MONTH -> R.string.stats_insight_over_caption_month
    StatsPeriod.YEAR -> R.string.stats_insight_over_caption_year
}

// ─── Donut hero ─────────────────────────────────────────────────────────────

@Composable
private fun DonutHero(state: StatsUiState) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            MultiSliceDonut(
                slices = state.summaries.map {
                    DonutSlice(it.total.toFloat(), parseHexColor(it.color))
                },
                modifier = Modifier.size(260.dp),
            )
            DonutCenter(state = state)
        }
    }
}

private data class DonutSlice(val value: Float, val color: Color)

@Composable
private fun MultiSliceDonut(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    val totalRaw = slices.sumOf { it.value.toDouble() }.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalRaw > 0f) 1f else 0f,
        animationSpec = tween(900, easing = LinearOutSlowInEasing),
        label = "donutSweep",
    )
    val gapDeg = 2f
    // Capture the empty-state track color outside the DrawScope — MaterialTheme reads can
    // only run inside an @Composable scope, and DrawScope is not one.
    val emptyTrackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val strokeWidth = 30.dp.toPx()
        // Inset the arc bounds by half the stroke so the ring paints inside the layout
        // box instead of bleeding ~15dp into the neighbors' spacing (same fix as the
        // Home budget ring).
        val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        if (totalRaw <= 0f) {
            drawArc(
                color = emptyTrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            return@Canvas
        }

        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value / totalRaw) * 360f * animatedProgress
            val sweepWithGap = (sweep - gapDeg).coerceAtLeast(0.5f)
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweepWithGap,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun DonutCenter(state: StatsUiState) {
    val totalLabel = when (state.period) {
        StatsPeriod.WEEK -> stringResource(R.string.stats_total_period_week)
        StatsPeriod.MONTH -> stringResource(R.string.stats_total_month)
        StatsPeriod.YEAR -> stringResource(R.string.stats_total_year)
    }
    val vsLabelRes = when (state.period) {
        StatsPeriod.WEEK -> R.string.stats_vs_prev_week
        StatsPeriod.MONTH -> R.string.stats_vs_prev_month
        StatsPeriod.YEAR -> R.string.stats_vs_prev_year
    }
    val integer = state.total.toLong()
    val cents = ((state.total - integer) * 100).toLong().coerceIn(0, 99)
    val integerFormatted = java.text.NumberFormat.getNumberInstance(
        java.util.Locale.forLanguageTag("es-PE"),
    ).format(integer)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = totalLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
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
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = integerFormatted,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.2).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = ".%02d".format(cents),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.previousTotal > 0.0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(vsLabelRes, money(state.previousTotal)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ─── Category bar row ───────────────────────────────────────────────────────

@Composable
private fun CategoryBarRow(summary: CategorySummary, total: Double) {
    val color = parseHexColor(summary.color)
    val share = if (total > 0.0) (summary.total / total).toFloat() else 0f
    val animatedShare by animateFloatAsState(
        targetValue = share,
        animationSpec = tween(600, easing = LinearOutSlowInEasing),
        label = "categoryBar",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Vertical color bar on the left — the category swatch.
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        // Title + progress underline
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedShare.coerceIn(0.04f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = currencySymbol(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 1.dp),
                )
                Spacer(Modifier.padding(1.dp))
                Text(
                    text = "%.2f".format(summary.total),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "%.0f%%".format(share * 100f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ─── Patrón semanal ─────────────────────────────────────────────────────────

@Composable
private fun WeeklyPatternCard(state: StatsUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stats_weekly_pattern_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                WeekdayChart(state = state)
                Spacer(Modifier.height(12.dp))
                state.peakWeekday?.let { peak ->
                    Text(
                        text = weeklyPatternCaption(peak.dayOfWeek, peak.average),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayChart(state: StatsUiState) {
    val animatedReveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "weekdayChartReveal",
    )

    val labels = listOf(
        stringResource(R.string.stats_weekday_mon),
        stringResource(R.string.stats_weekday_tue),
        stringResource(R.string.stats_weekday_wed),
        stringResource(R.string.stats_weekday_thu),
        stringResource(R.string.stats_weekday_fri),
        stringResource(R.string.stats_weekday_sat),
        stringResource(R.string.stats_weekday_sun),
    )
    val max = (state.weekdayPattern.maxOfOrNull { it.average } ?: 0.0).coerceAtLeast(0.0001)

    Column(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        // Chart area
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            state.weekdayPattern.forEachIndexed { i, point ->
                val isPeak = point.average > 0 && point.average == state.peakWeekday?.average
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (isPeak && point.average > 0) {
                        // Coral dot above the peak day — recurring brand motif
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(CoralRamp500),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    val heightFraction = (point.average / max).toFloat() * animatedReveal
                    val barColor = if (isPeak) CoralRamp500.copy(alpha = 0.3f) else TealRamp500.copy(alpha = 0.18f)
                    val barFillColor = if (isPeak) CoralRamp500 else TealRamp500
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeightFraction(if (point.average > 0) heightFraction.coerceAtLeast(0.05f) else 0.02f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barFillColor),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Day labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, label ->
                val isPeak = state.weekdayPattern.getOrNull(i)?.let {
                    it.average > 0 && it.average == state.peakWeekday?.average
                } == true
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (isPeak) CoralRamp500 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** `fillMaxHeight()` takes a 0..1 fraction; this helper just keeps the call site readable. */
private fun Modifier.fillMaxHeightFraction(fraction: Float) =
    this.fillMaxHeight(fraction.coerceIn(0f, 1f))

@Composable
private fun weeklyPatternCaption(dayOfWeek: Int, avg: Double): AnnotatedString {
    val fullName = stringResource(
        when (dayOfWeek) {
            1 -> R.string.stats_weekday_mon_full
            2 -> R.string.stats_weekday_tue_full
            3 -> R.string.stats_weekday_wed_full
            4 -> R.string.stats_weekday_thu_full
            5 -> R.string.stats_weekday_fri_full
            6 -> R.string.stats_weekday_sat_full
            else -> R.string.stats_weekday_sun_full
        },
    )
    val raw = stringResource(R.string.stats_weekly_pattern_caption, fullName, money(avg))
    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            val openIdx = raw.indexOf("<b>", i)
            if (openIdx == -1) {
                append(raw.substring(i))
                break
            }
            append(raw.substring(i, openIdx))
            val closeIdx = raw.indexOf("</b>", openIdx)
            if (closeIdx == -1) {
                append(raw.substring(openIdx))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                append(raw.substring(openIdx + 3, closeIdx))
            }
            i = closeIdx + 4
        }
    }
}

// ─── Empty state ────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    IllustratedEmptyState(
        icon = Icons.Outlined.QueryStats,
        title = stringResource(R.string.stats_empty_title),
        subtitle = stringResource(R.string.stats_empty_subtitle),
        accent = MaterialTheme.colorScheme.secondary,
    )
}

@Suppress("unused")
private val previewMarker: Color = AccentRose
