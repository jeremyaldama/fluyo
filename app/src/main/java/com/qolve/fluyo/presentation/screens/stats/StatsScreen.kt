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
import com.qolve.fluyo.presentation.theme.NeutralRamp200
import com.qolve.fluyo.presentation.theme.NeutralRamp500
import com.qolve.fluyo.presentation.theme.NeutralRamp700
import com.qolve.fluyo.presentation.theme.NeutralRamp900
import com.qolve.fluyo.presentation.theme.TealRamp500
import com.qolve.fluyo.presentation.util.formatPen
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(NeutralRamp200.copy(alpha = 0.55f))
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
            color = if (selected) NeutralRamp900 else NeutralRamp500,
        )
    }
}

// ─── Insight banner ─────────────────────────────────────────────────────────

@Composable
private fun InsightBanner(state: StatsUiState) {
    val delta = state.deltaPct
    val isUnder = (delta ?: 0f) < -0.5f
    val isOver = (delta ?: 0f) > 0.5f

    val emoji = when {
        isUnder -> "👏"
        isOver -> "⚠️"
        delta == null -> "✨"
        else -> "🟰"
    }
    val (background, foreground, accentBg) = when {
        isUnder -> Triple(
            AccentLime.copy(alpha = 0.18f),
            NeutralRamp900,
            AccentLime.copy(alpha = 0.30f),
        )
        isOver -> Triple(
            CoralRamp500.copy(alpha = 0.14f),
            NeutralRamp900,
            CoralRamp500.copy(alpha = 0.26f),
        )
        else -> Triple(
            NeutralRamp200.copy(alpha = 0.6f),
            NeutralRamp900,
            NeutralRamp200,
        )
    }

    val (title, caption) = when {
        delta == null -> stringResource(R.string.stats_insight_no_baseline) to ""
        isUnder -> stringResource(R.string.stats_insight_under_pct, delta.absoluteValue.toInt()) to
            stringResource(periodUnderCaption(state.period))
        isOver -> stringResource(R.string.stats_insight_over_pct, delta.toInt()) to
            stringResource(periodOverCaption(state.period))
        else -> stringResource(R.string.stats_insight_flat) to ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(14.dp),
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foreground,
            )
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeutralRamp700,
                )
            }
        }
    }
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

    Canvas(modifier = modifier) {
        val strokeWidth = 30.dp.toPx()
        if (totalRaw <= 0f) {
            drawArc(
                color = NeutralRamp200,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
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
            color = NeutralRamp500,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "S/",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = NeutralRamp500,
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
                color = NeutralRamp900,
            )
            Text(
                text = ".%02d".format(cents),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = NeutralRamp500,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.previousTotal > 0.0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(vsLabelRes, formatPen(state.previousTotal)),
                style = MaterialTheme.typography.labelMedium,
                color = NeutralRamp500,
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
            .padding(vertical = 14.dp, horizontal = 14.dp),
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
                color = NeutralRamp900,
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
                    text = "S/",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NeutralRamp500,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
                Spacer(Modifier.padding(1.dp))
                Text(
                    text = "%.2f".format(summary.total),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = NeutralRamp900,
                )
            }
            Text(
                text = "%.0f%%".format(share * 100f),
                style = MaterialTheme.typography.labelSmall,
                color = NeutralRamp500,
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
                        color = NeutralRamp700,
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
                        color = if (isPeak) CoralRamp500 else NeutralRamp500,
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
    val raw = stringResource(R.string.stats_weekly_pattern_caption, fullName, formatPen(avg))
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
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NeutralRamp900)) {
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
