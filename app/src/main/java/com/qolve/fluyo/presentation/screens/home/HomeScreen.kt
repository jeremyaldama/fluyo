package com.qolve.fluyo.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.presentation.screens.home.components.BudgetCircle
import com.qolve.fluyo.presentation.screens.home.components.ExpenseRow
import com.qolve.fluyo.presentation.screens.home.components.HomeHeader
import com.qolve.fluyo.presentation.screens.home.components.StatsStrip
import com.qolve.fluyo.presentation.theme.AccentLime
import com.qolve.fluyo.presentation.theme.CoralRamp100
import com.qolve.fluyo.presentation.theme.CoralRamp500
import com.qolve.fluyo.presentation.theme.NeutralRamp500
import com.qolve.fluyo.presentation.theme.NeutralRamp900
import com.qolve.fluyo.presentation.theme.TealRamp100
import com.qolve.fluyo.presentation.theme.TealRamp500
import com.qolve.fluyo.presentation.util.formatPen
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Home screen — the dashboard the user lands on after sign-in.
 *
 * Layout (per the redesigned mockup):
 *   1. Header — F. mark, "Hola," in serif italic, name in bold, avatar.
 *   2. Hero ring — animated arc with coral dot endpoint. Center = "TE QUEDA / S/ X.XX / de S/ Y".
 *   3. Pace chip — "Vas bien · S/ N al día" or warning variant when over budget.
 *   4. Stats strip — three cards (ESTA SEMANA / PROMEDIO / PRÓX. COBRO).
 *   5. Movimientos — section title + "Ver todo →" link, day-grouped expenses.
 *   6. Empty state when there are zero expenses this month: shows the full-budget ring +
 *      a ghost preview row instead of the stats strip.
 */
@Composable
fun HomeScreen(
    onAvatarClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val grouped: List<Pair<LocalDate, List<Expense>>> = remember(state.recentExpenses) {
        state.recentExpenses.groupBy { it.expenseDate }
            .toList()
            .sortedByDescending { it.first }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HomeHeader(
                displayName = state.displayName,
                avatarUrl = state.avatarUrl,
                onAvatarClick = onAvatarClick,
            )
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BudgetCircle(breakdown = state.breakdown, size = 240.dp)
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PaceChip(breakdown = state.breakdown)
            }
        }

        if (state.recentExpenses.isEmpty()) {
            // First-day empty state — no spending yet
            item {
                EmptyDayPreview()
            }
        } else {
            // Stats strip + Movimientos list for the normal case
            item {
                StatsStrip(
                    weekSpend = recentWeekSpend(state.recentExpenses),
                    weekDeltaPct = null, // baseline not yet computed; safe to wire later
                    dailyAvg = monthlyDailyAverage(state.breakdown),
                    daysUntilNextPay = null, // no salary schedule data yet
                )
            }
            item {
                // "Ver todo →" intentionally omitted until an all-expenses screen exists.
                // Showing a teal text link that does nothing would erode trust in every
                // other teal link on the app. Re-introduce when Routes.ALL_EXPENSES lands.
                Text(
                    text = stringResource(R.string.home_movements_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
            grouped.forEach { (date, expenses) ->
                item(key = "header-$date") { DayHeader(date = date) }
                item(key = "card-$date") {
                    DayExpensesCard(expenses = expenses, categoriesById = state.categoriesById)
                }
            }
        }
    }
}

@Composable
private fun PaceChip(breakdown: MonthlyBreakdown) {
    val today = remember { LocalDate.now() }
    val ym = remember(today) { YearMonth.from(today) }
    val daysLeft = (ym.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
    val dailyPace = breakdown.remaining.coerceAtLeast(0.0) / daysLeft

    val (text, color) = when {
        breakdown.monthlyBudget <= 0.0 -> stringResource(R.string.home_pace_no_budget_chip) to NeutralRamp500
        breakdown.isOverBudget -> stringResource(R.string.home_pace_over_chip) to CoralRamp500
        else -> stringResource(R.string.home_pace_ok_chip, formatPen(dailyPace)) to TealRamp500
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (breakdown.isOverBudget) CoralRamp100 else TealRamp100)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (breakdown.isOverBudget) CoralRamp500 else AccentLime),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

/**
 * First-day preview when the user hasn't registered any expense yet.
 * Shows a coral-tinted "Almuerzo" ghost row to hint at what the list will look like.
 */
@Composable
private fun EmptyDayPreview() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.home_empty_preview_header),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.0.sp,
            ),
            color = NeutralRamp500,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp).graphicsAlpha(0.45f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CoralRamp100),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = null,
                        tint = CoralRamp500,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_empty_preview_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.home_empty_preview_amount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NeutralRamp500,
                )
            }
        }
    }
}

/** Compose's `Modifier.alpha` lives in `androidx.compose.ui.draw` — aliased here to keep
 *  the call site readable ("ghost row at 45% opacity") without leaking implementation
 *  details into HomeScreen. */
private fun Modifier.graphicsAlpha(value: Float) = this.alpha(value)

@Composable
private fun DayHeader(date: LocalDate) {
    val label = when (date) {
        LocalDate.now() -> stringResource(R.string.home_day_today_caps)
        LocalDate.now().minusDays(1) -> stringResource(R.string.home_day_yesterday_caps)
        else -> date.format(dayHeaderFmt).uppercase(Locale.forLanguageTag("es-PE"))
    }
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp, ),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.0.sp,
        ),
        color = NeutralRamp500,
    )
}

@Composable
private fun DayExpensesCard(
    expenses: List<Expense>,
    categoriesById: Map<String, Category>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            expenses.forEachIndexed { index, expense ->
                ExpenseRow(
                    expense = expense,
                    category = expense.categoryId?.let { categoriesById[it] },
                )
                if (index != expenses.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

// ─── Derived stats helpers ──────────────────────────────────────────────────
//
// Computed in Compose rather than the ViewModel because they're cheap pure functions over
// the existing state — adding new fields would just churn the ViewModel for marginal benefit.

private fun recentWeekSpend(expenses: List<Expense>): Double {
    val cutoff = LocalDate.now().minusDays(6)
    return expenses
        .filter { !it.expenseDate.isBefore(cutoff) }
        .sumOf { it.amount }
}

private fun monthlyDailyAverage(breakdown: MonthlyBreakdown): Double {
    val today = LocalDate.now()
    val daysElapsed = today.dayOfMonth.coerceAtLeast(1)
    return breakdown.totalSpent / daysElapsed.toDouble()
}

private val dayHeaderFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"))
