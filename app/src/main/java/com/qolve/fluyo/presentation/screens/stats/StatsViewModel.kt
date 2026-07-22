package com.qolve.fluyo.presentation.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.CategorySummary
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.sumMoney
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.qolve.fluyo.domain.time.FluyoTime
import java.time.temporal.TemporalAdjusters
import java.math.RoundingMode
import javax.inject.Inject

enum class StatsPeriod { WEEK, MONTH, YEAR }

private data class DateRange(val from: LocalDate, val to: LocalDate)

private fun StatsPeriod.currentRange(today: LocalDate = FluyoTime.today()): DateRange = when (this) {
    StatsPeriod.WEEK -> DateRange(today.minusDays(6), today)
    StatsPeriod.MONTH -> DateRange(today.with(TemporalAdjusters.firstDayOfMonth()), today)
    StatsPeriod.YEAR -> DateRange(today.with(TemporalAdjusters.firstDayOfYear()), today)
}

private fun StatsPeriod.previousRange(today: LocalDate = FluyoTime.today()): DateRange = when (this) {
    StatsPeriod.WEEK -> DateRange(today.minusDays(13), today.minusDays(7))
    StatsPeriod.MONTH -> {
        val firstOfThis = today.with(TemporalAdjusters.firstDayOfMonth())
        val lastOfPrev = firstOfThis.minusDays(1)
        val firstOfPrev = lastOfPrev.with(TemporalAdjusters.firstDayOfMonth())
        DateRange(firstOfPrev, lastOfPrev)
    }
    StatsPeriod.YEAR -> {
        val firstOfThis = today.with(TemporalAdjusters.firstDayOfYear())
        val lastOfPrev = firstOfThis.minusDays(1)
        val firstOfPrev = lastOfPrev.with(TemporalAdjusters.firstDayOfYear())
        DateRange(firstOfPrev, lastOfPrev)
    }
}

/** One point on the daily-spend sparkline. Date is the calendar day, total is sum of expenses that day. */
data class DailyPoint(val date: LocalDate, val total: MoneyAmount)

/** One point on the weekday-average chart. `dayOfWeek` is 1..7 (Monday..Sunday) — matches
 *  `java.time.DayOfWeek.value`. `average` is mean spend on that weekday across the period. */
data class WeekdayPoint(val dayOfWeek: Int, val average: MoneyAmount)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val total: MoneyAmount = MoneyAmount.ZERO,
    val previousTotal: MoneyAmount = MoneyAmount.ZERO,
    val summaries: List<CategorySummary> = emptyList(),
    val daily: List<DailyPoint> = emptyList(),
    val weekdayPattern: List<WeekdayPoint> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Signed percent delta vs previous period; null when previous period has no data. */
    val deltaPct: Float?
        get() = if (previousTotal <= MoneyAmount.ZERO) null
        else (total - previousTotal).toBigDecimal()
            .multiply(java.math.BigDecimal.valueOf(100L))
            .divide(previousTotal.toBigDecimal(), 6, RoundingMode.HALF_EVEN)
            .toFloat()

    val isUnderPrevious: Boolean get() = (deltaPct ?: 0f) < 0f

    /** Highest-spend category (already sorted descending by total in load()). */
    val topCategory: CategorySummary? get() = summaries.firstOrNull()

    /** Peak day in the current period, or null when no expenses. */
    val peakDay: DailyPoint?
        get() = daily.maxByOrNull { it.total }?.takeIf { it.total > MoneyAmount.ZERO }

    /** Highest-spend weekday in the current period (or null when nothing logged). */
    val peakWeekday: WeekdayPoint?
        get() = weekdayPattern.maxByOrNull { it.average }
            ?.takeIf { it.average > MoneyAmount.ZERO }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        load()
    }

    fun selectPeriod(period: StatsPeriod) {
        if (_state.value.period == period) return
        _state.update {
            StatsUiState(period = period, isLoading = true)
        }
        load(force = true)
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun refresh() = load(force = false)

    private fun load(force: Boolean = false) {
        if (!force && loadJob?.isActive == true) return
        if (force) loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val period = _state.value.period
            val current = period.currentRange()
            val previous = period.previousRange()

            val currentExpenses = expenseRepository
                .loadByDateRange(current.from, current.to)
                .getOrElse {
                    publishLoadFailure(generation, "No se pudo cargar el período")
                    return@launch
                }
            val previousExpenses = expenseRepository
                .loadByDateRange(previous.from, previous.to)
                .getOrElse { error ->
                    publishLoadFailure(generation, "No se pudo cargar el período anterior")
                    return@launch
                }

            categoryRepository.refresh().getOrElse { error ->
                publishLoadFailure(generation, "No se pudieron cargar las categorías")
                return@launch
            }
            val categories = categoryRepository.observeCategories().first()
            val byId = categories.associateBy { it.id }

            val summaries = currentExpenses
                .groupBy { it.categoryId }
                .map { (id, items) ->
                    val cat = id?.let { byId[it] }
                    CategorySummary(
                        categoryId = id,
                        name = cat?.name ?: "Sin categoría",
                        color = cat?.color ?: "#78909C",
                        icon = cat?.icon ?: "tag",
                        total = items.map { it.amount }.sumMoney(),
                        count = items.size,
                    )
                }
                .sortedByDescending { it.total }

            // Build a continuous daily series so the sparkline shows zero-spend days too.
            val daily = buildDailyPoints(current.from, current.to, currentExpenses)
            val weekdayPattern = buildWeekdayPattern(
                from = current.from,
                to = current.to,
                expenses = currentExpenses,
            )

            // A repository/HTTP implementation may finish after cancellation. Never let
            // an obsolete request overwrite the period the user currently selected.
            if (generation != loadGeneration || _state.value.period != period) return@launch

            _state.update {
                it.copy(
                    hasLoaded = true,
                    isLoading = false,
                    total = currentExpenses.map { e -> e.amount }.sumMoney(),
                    previousTotal = previousExpenses.map { e -> e.amount }.sumMoney(),
                    summaries = summaries,
                    daily = daily,
                    weekdayPattern = weekdayPattern,
                )
            }
        }
    }

    private fun publishLoadFailure(generation: Long, message: String) {
        if (generation != loadGeneration) return
        _state.update { it.copy(isLoading = false, errorMessage = message) }
    }

    private fun buildDailyPoints(
        from: LocalDate,
        to: LocalDate,
        expenses: List<com.qolve.fluyo.domain.model.Expense>,
    ): List<DailyPoint> {
        val byDay = expenses.groupBy { it.expenseDate }
            .mapValues { (_, v) -> v.map { it.amount }.sumMoney() }
        val points = mutableListOf<DailyPoint>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            points += DailyPoint(cursor, byDay[cursor] ?: MoneyAmount.ZERO)
            cursor = cursor.plusDays(1)
        }
        return points
    }
}

/**
 * Day-of-week averages over the complete calendar range, including days with zero spend.
 * Always emits Mon..Sun so the chart remains stable for sparse data.
 */
internal fun buildWeekdayPattern(
    from: LocalDate,
    to: LocalDate,
    expenses: List<com.qolve.fluyo.domain.model.Expense>,
): List<WeekdayPoint> {
    require(!to.isBefore(from)) { "Invalid date range: $from..$to" }

    val totals = Array(7) { MoneyAmount.ZERO }
    val occurrences = IntArray(7)

    var cursor = from
    while (!cursor.isAfter(to)) {
        occurrences[cursor.dayOfWeek.value - 1]++
        cursor = cursor.plusDays(1)
    }

    for (expense in expenses) {
        if (expense.expenseDate.isBefore(from) || expense.expenseDate.isAfter(to)) continue
        val index = expense.expenseDate.dayOfWeek.value - 1
        totals[index] = totals[index] + expense.amount
    }

    return (0..6).map { index ->
        val average = if (occurrences[index] == 0) {
            MoneyAmount.ZERO
        } else {
            totals[index].dividedBy(occurrences[index].toLong(), RoundingMode.HALF_EVEN)
        }
        WeekdayPoint(dayOfWeek = index + 1, average = average)
    }
}
