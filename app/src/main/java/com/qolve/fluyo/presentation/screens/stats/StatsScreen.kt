package com.qolve.fluyo.presentation.screens.stats

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import androidx.compose.material.icons.outlined.QueryStats
import com.qolve.fluyo.domain.model.CategorySummary
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.screens.stats.components.DonutChart
import com.qolve.fluyo.presentation.screens.stats.components.DonutSlice
import com.qolve.fluyo.presentation.theme.ErrorRed
import com.qolve.fluyo.presentation.theme.SuccessGreen
import com.qolve.fluyo.presentation.util.formatPen
import com.qolve.fluyo.presentation.util.iconForToken
import com.qolve.fluyo.presentation.util.parseHexColor
import kotlin.math.absoluteValue

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header() }

        item {
            PeriodToggle(
                selected = state.period,
                onSelect = viewModel::selectPeriod,
            )
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val slices = state.summaries.map {
                            DonutSlice(it.total.toFloat(), parseHexColor(it.color))
                        }
                        DonutChart(
                            slices = slices,
                            centerLabel = stringResource(
                                when (state.period) {
                                    StatsPeriod.WEEK -> R.string.stats_total_week
                                    StatsPeriod.MONTH -> R.string.stats_total_month
                                },
                            ),
                            centerValue = formatPen(state.total),
                        )
                    }
                }
            }
        }

        if (!state.isLoading) {
            item { ComparisonCard(state) }

            if (state.summaries.isEmpty()) {
                item { EmptyState() }
            } else {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.stats_by_category),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                items(state.summaries, key = { it.categoryId ?: it.name }) { summary ->
                    CategoryLegendRow(summary = summary, total = state.total)
                }
            }
        }
    }
}

@Composable
private fun StatsViewModel.uiState() = state.collectAsStateWithLifecycle()

@Composable
private fun Header() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = stringResource(R.string.stats_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeriodToggle(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val options = listOf(StatsPeriod.WEEK, StatsPeriod.MONTH)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(state: StatsUiState) {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.stats_previous_total,
                        formatPen(state.previousTotal),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(summary: CategorySummary, total: Double) {
    val color = parseHexColor(summary.color)
    val icon = iconForToken(summary.icon)
    val share = summary.share(total)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.stats_count_inline, summary.count),
                style = MaterialTheme.typography.bodySmall,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    IllustratedEmptyState(
        icon = Icons.Outlined.QueryStats,
        title = stringResource(R.string.stats_empty_title),
        subtitle = stringResource(R.string.stats_empty_subtitle),
        accent = MaterialTheme.colorScheme.secondary,
    )
}
