package com.qolve.fluyo.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qolve.fluyo.presentation.components.BudgetEditDialog
import com.qolve.fluyo.presentation.components.ExtraIncomeDialog
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.sumMoney
import com.qolve.fluyo.presentation.screens.home.components.BudgetCircle
import com.qolve.fluyo.presentation.screens.home.components.DayExpensesCard
import com.qolve.fluyo.presentation.screens.home.components.DayHeader
import com.qolve.fluyo.presentation.screens.home.components.HomeHeader
import com.qolve.fluyo.presentation.screens.home.components.StatsStrip
import com.qolve.fluyo.presentation.theme.AccentLime
import com.qolve.fluyo.presentation.theme.CoralRamp100
import com.qolve.fluyo.presentation.theme.CoralRamp500
// Neutral ramp imports removed — see notes in ProfileScreen / HomeHeader / StatsStrip.
import com.qolve.fluyo.presentation.theme.TealRamp100
import com.qolve.fluyo.presentation.theme.TealRamp500
import com.qolve.fluyo.presentation.util.money
import java.time.LocalDate
import com.qolve.fluyo.domain.time.FluyoTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.math.RoundingMode

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
    onExpenseClick: (String) -> Unit = {},
    onViewAll: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val budgetDialog by viewModel.budgetDialog.collectAsStateWithLifecycle()

    val grouped: List<Pair<LocalDate, List<Expense>>> = remember(state.recentExpenses) {
        state.recentExpenses.groupBy { it.expenseDate }
            .toList()
            .sortedByDescending { it.first }
    }

    // External writes (WhatsApp bot, another device) land directly in Supabase — re-query
    // whenever the user comes back to the app so Home never shows stale totals.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.errorMessage != null && !state.hasLoaded) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        } else {
        LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.errorMessage?.let { message ->
            item(key = "refresh-error") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
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
                BudgetCircle(
                    breakdown = state.breakdown,
                    size = 240.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = stringResource(R.string.home_budget_edit_a11y),
                            onClick = viewModel::openBudgetDialog,
                        ),
                )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_movements_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = stringResource(R.string.home_view_all),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onViewAll)
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    )
                }
            }
            grouped.forEach { (date, expenses) ->
                item(key = "header-$date") { DayHeader(date = date) }
                item(key = "card-$date") {
                    DayExpensesCard(
                        expenses = expenses,
                        categoriesById = state.categoriesById,
                        onExpenseClick = { onExpenseClick(it.id) },
                    )
                }
            }
        }
    }
        }
    }

    if (budgetDialog.showBudget) {
        BudgetEditDialog(
            input = budgetDialog.budgetInput,
            isSaving = budgetDialog.isSaving,
            error = budgetDialog.error,
            extraIncome = budgetDialog.monthExtrasTotal,
            onInputChange = viewModel::onBudgetInputChange,
            onDismiss = viewModel::closeBudgetDialog,
            onConfirm = viewModel::saveBudget,
            onAddExtra = viewModel::openExtraDialog,
        )
    }

    if (budgetDialog.showExtra) {
        ExtraIncomeDialog(
            amountInput = budgetDialog.extraAmountInput,
            noteInput = budgetDialog.extraNoteInput,
            isSaving = budgetDialog.isSavingExtra,
            error = budgetDialog.error,
            extras = budgetDialog.monthExtras,
            deletingExtraIds = budgetDialog.deletingExtraIds,
            onAmountChange = viewModel::onExtraAmountChange,
            onNoteChange = viewModel::onExtraNoteChange,
            onDelete = viewModel::deleteExtra,
            onDismiss = viewModel::closeExtraDialog,
            onConfirm = viewModel::saveExtra,
        )
    }
}

@Composable
private fun PaceChip(breakdown: MonthlyBreakdown) {
    val today = remember { FluyoTime.today() }
    val ym = remember(today) { YearMonth.from(today) }
    val daysLeft = (ym.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
    val dailyPace = breakdown.remaining
        .coerceAtLeast(MoneyAmount.ZERO)
        .dividedBy(daysLeft.toLong(), RoundingMode.HALF_EVEN)

    val (text, color) = when {
        breakdown.monthlyBudget <= MoneyAmount.ZERO -> stringResource(R.string.home_pace_no_budget_chip) to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        breakdown.isOverBudget -> stringResource(R.string.home_pace_over_chip) to CoralRamp500
        else -> stringResource(R.string.home_pace_ok_chip, money(dailyPace)) to TealRamp500
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Compose's `Modifier.alpha` lives in `androidx.compose.ui.draw` — aliased here to keep
 *  the call site readable ("ghost row at 45% opacity") without leaking implementation
 *  details into HomeScreen. */
private fun Modifier.graphicsAlpha(value: Float) = this.alpha(value)

// DayHeader / DayExpensesCard moved to components/DayExpenseList.kt — shared with
// the all-expenses history screen.

// ─── Derived stats helpers ──────────────────────────────────────────────────
//
// Computed in Compose rather than the ViewModel because they're cheap pure functions over
// the existing state — adding new fields would just churn the ViewModel for marginal benefit.

private fun recentWeekSpend(expenses: List<Expense>): MoneyAmount {
    val cutoff = FluyoTime.today().minusDays(6)
    return expenses
        .filter { !it.expenseDate.isBefore(cutoff) }
        .map { it.amount }
        .sumMoney()
}

private fun monthlyDailyAverage(breakdown: MonthlyBreakdown): MoneyAmount {
    val today = FluyoTime.today()
    val daysElapsed = today.dayOfMonth.coerceAtLeast(1)
    return breakdown.totalSpent.dividedBy(daysElapsed.toLong(), RoundingMode.HALF_EVEN)
}
