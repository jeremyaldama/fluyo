package com.qolve.fluyo.presentation.screens.stats

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.CategorySummary
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.screens.stats.components.DonutChart
import com.qolve.fluyo.presentation.screens.stats.components.DonutSlice
import com.qolve.fluyo.presentation.theme.ErrorRed
import com.qolve.fluyo.presentation.theme.SuccessGreen
import com.qolve.fluyo.presentation.util.formatPen
import com.qolve.fluyo.presentation.util.iconForToken
import com.qolve.fluyo.presentation.util.parseHexColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Header(
                period = state.period,
                onSelect = viewModel::selectPeriod,
            )
        }

        item { Spacer(Modifier.height(0.dp)) }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
        } else if (state.summaries.isEmpty()) {
            item { EmptyState() }
        } else {
            item {
                DonutHero(state = state)
            }

            item {
                TopCategoryBanner(state = state)
            }

            item {
                ComparisonRow(state = state)
            }

            if (state.daily.size >= 2) {
                item {
                    DailyTrendCard(state = state)
                }
            }

            item {
                SectionTitle(stringResource(R.string.stats_by_category))
            }

            items(state.summaries, key = { it.categoryId ?: it.name }) { summary ->
                CategoryBarRow(summary = summary, total = state.total)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header (title + subtitle + inline period pill)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Header(
    period: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.stats_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        CompactPeriodPill(selected = period, onSelect = onSelect)
    }
}

@Composable
private fun CompactPeriodPill(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val options = listOf(StatsPeriod.WEEK, StatsPeriod.MONTH)
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    text = stringResource(
                        when (period) {
                            StatsPeriod.WEEK -> R.string.stats_period_week
                            StatsPeriod.MONTH -> R.string.stats_period_month
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Donut hero — centered, smaller, total below
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DonutHero(state: StatsUiState) {
    val slices = state.summaries.map {
        DonutSlice(it.total.toFloat(), parseHexColor(it.color))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DonutChart(
            slices = slices,
            centerLabel = stringResource(
                when (state.period) {
                    StatsPeriod.WEEK -> R.string.stats_total_week
                    StatsPeriod.MONTH -> R.string.stats_total_month
                },
            ),
            centerValue = formatPen(state.total),
            modifier = Modifier.size(180.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-category banner — coral-tinted insight, the "delight moment"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopCategoryBanner(state: StatsUiState) {
    val top = state.topCategory ?: return
    if (state.total <= 0.0) return
    val color = parseHexColor(top.color)
    val icon = iconForToken(top.icon)
    val share = (top.total / state.total) * 100.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.14f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_top_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = top.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPen(top.total),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = color,
                )
                Text(
                    text = stringResource(R.string.stats_top_category_share, "%.0f".format(share)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Comparison — inline row (not a heavy card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComparisonRow(state: StatsUiState) {
    val delta = state.deltaPct
    val (icon: ImageVector, color, message) = when {
        delta == null -> Triple(
            Icons.AutoMirrored.Outlined.TrendingFlat,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.stats_compare_no_baseline),
        )
        delta < -0.5f -> Triple(
            Icons.AutoMirrored.Outlined.TrendingDown,
            SuccessGreen,
            stringResource(R.string.stats_compare_under, "%.0f".format(delta.absoluteValue)),
        )
        delta > 0.5f -> Triple(
            Icons.AutoMirrored.Outlined.TrendingUp,
            ErrorRed,
            stringResource(R.string.stats_compare_over, "%.0f".format(delta.absoluteValue)),
        )
        else -> Triple(
            Icons.AutoMirrored.Outlined.TrendingFlat,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.stats_compare_flat),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = stringResource(R.string.stats_previous_total, formatPen(state.previousTotal)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Daily trend — Canvas-based sparkline filling the previously-empty space
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DailyTrendCard(state: StatsUiState) {
    val accent = MaterialTheme.colorScheme.primary
    val peak = state.peakDay
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.stats_daily_trend_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                if (peak != null) {
                    val fmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE")) }
                    Text(
                        text = stringResource(
                            R.string.stats_daily_trend_peak,
                            "${peak.date.format(fmt)} · ${formatPen(peak.total)}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(
                values = state.daily.map { it.total.toFloat() },
                color = accent,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "sparklineReveal",
    )
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)

    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1).toFloat()

        // Reveal clipping: draw only up to `animatedProgress` of width.
        val revealW = w * animatedProgress

        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            // Top padding so the peak doesn't kiss the canvas edge.
            val y = h - (v / max) * (h * 0.85f) - h * 0.05f
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, h)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(w, h)
        area.close()

        // Soft fill under the curve, clipped to the animated reveal width.
        clipRect(left = 0f, top = 0f, right = revealW, bottom = h) {
            drawPath(
                path = area,
                color = color.copy(alpha = 0.18f),
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // Endpoint dot at the latest reveal position
            val lastIdx = (animatedProgress * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
            val lx = lastIdx * stepX
            val ly = h - (values[lastIdx] / max) * (h * 0.85f) - h * 0.05f
            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(lx, ly))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category bar row — horizontal progress bar instead of plain text row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun CategoryBarRow(summary: CategorySummary, total: Double) {
    val color = parseHexColor(summary.color)
    val icon = iconForToken(summary.icon)
    val share = if (total > 0.0) (summary.total / total).toFloat() else 0f
    val animatedShare by animateFloatAsState(
        targetValue = share,
        animationSpec = tween(600, easing = LinearOutSlowInEasing),
        label = "categoryBar",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.stats_count_inline, summary.count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPen(summary.total),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "%.0f%%".format(share * 100f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Custom progress bar (skip Material LinearProgressIndicator — we want the brand color + rounded caps + fill animation).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedShare.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    IllustratedEmptyState(
        icon = Icons.Outlined.QueryStats,
        title = stringResource(R.string.stats_empty_title),
        subtitle = stringResource(R.string.stats_empty_subtitle),
        accent = MaterialTheme.colorScheme.secondary,
    )
}
