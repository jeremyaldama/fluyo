package com.qolve.fluyo.presentation.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.CategorySummary
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class StatsPeriod { WEEK, MONTH, YEAR }

private data class DateRange(val from: LocalDate, val to: LocalDate)

private fun StatsPeriod.currentRange(today: LocalDate = LocalDate.now()): DateRange = when (this) {
    StatsPeriod.WEEK -> DateRange(today.minusDays(6), today)
    StatsPeriod.MONTH -> DateRange(today.with(TemporalAdjusters.firstDayOfMonth()), today)
    StatsPeriod.YEAR -> DateRange(today.with(TemporalAdjusters.firstDayOfYear()), today)
}

private fun StatsPeriod.previousRange(today: LocalDate = LocalDate.now()): DateRange = when (this) {
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
data class DailyPoint(val date: LocalDate, val total: Double)

/** One point on the weekday-average chart. `dayOfWeek` is 1..7 (Monday..Sunday) — matches
 *  `java.time.DayOfWeek.value`. `average` is mean spend on that weekday across the period. */
data class WeekdayPoint(val dayOfWeek: Int, val average: Double)

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val isLoading: Boolean = true,
    val total: Double = 0.0,
    val previousTotal: Double = 0.0,
    val summaries: List<CategorySummary> = emptyList(),
    val daily: List<DailyPoint> = emptyList(),
    val weekdayPattern: List<WeekdayPoint> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Signed percent delta vs previous period; null when previous period has no data. */
    val deltaPct: Float?
        get() = if (previousTotal <= 0.0) null
        else (((total - previousTotal) / previousTotal) * 100.0).toFloat()

    val isUnderPrevious: Boolean get() = (deltaPct ?: 0f) < 0f

    /** Highest-spend category (already sorted descending by total in load()). */
    val topCategory: CategorySummary? get() = summaries.firstOrNull()

    /** Peak day in the current period, or null when no expenses. */
    val peakDay: DailyPoint? get() = daily.maxByOrNull { it.total }?.takeIf { it.total > 0 }

    /** Highest-spend weekday in the current period (or null when nothing logged). */
    val peakWeekday: WeekdayPoint? get() = weekdayPattern.maxByOrNull { it.average }?.takeIf { it.average > 0 }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { categoryRepository.refresh() }
        load()
    }

    fun selectPeriod(period: StatsPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period) }
        load()
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val period = _state.value.period
            val current = period.currentRange()
            val previous = period.previousRange()

            val currentExpenses = expenseRepository
                .loadByDateRange(current.from, current.to)
                .getOrElse {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = it.errorMessage ?: "Error")
                    }
                    return@launch
                }
            val previousExpenses = expenseRepository
                .loadByDateRange(previous.from, previous.to)
                .getOrDefault(emptyList())

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
                        total = items.sumOf { it.amount },
                        count = items.size,
                    )
                }
                .sortedByDescending { it.total }

            // Build a continuous daily series so the sparkline shows zero-spend days too.
            val daily = buildDailyPoints(current.from, current.to, currentExpenses)
            val weekdayPattern = buildWeekdayPattern(currentExpenses)

            _state.update {
                it.copy(
                    isLoading = false,
                    total = currentExpenses.sumOf { e -> e.amount },
                    previousTotal = previousExpenses.sumOf { e -> e.amount },
                    summaries = summaries,
                    daily = daily,
                    weekdayPattern = weekdayPattern,
                )
            }
        }
    }

    /**
     * Day-of-week averages. We group every expense in the period by its weekday and divide
     * the sum by the number of *occurrences* of that weekday in the date range — so a
     * single big Friday doesn't dominate just because Fridays show up fewer times than
     * Mondays in a partial month.
     *
     * Always emits 7 entries (Mon..Sun) so the chart layout is stable even with sparse data.
     */
    private fun buildWeekdayPattern(
        expenses: List<com.qolve.fluyo.domain.model.Expense>,
    ): List<WeekdayPoint> {
        val totals = LongArray(7)  // sum in cents (avoid float drift on .sumOf)
        val counts = IntArray(7)   // number of distinct dates seen on that weekday
        val seenDatesPerWeekday = Array(7) { mutableSetOf<LocalDate>() }
        for (e in expenses) {
            val idx = e.expenseDate.dayOfWeek.value - 1 // 0..6 for Mon..Sun
            totals[idx] += (e.amount * 100).toLong()
            seenDatesPerWeekday[idx].add(e.expenseDate)
        }
        for (i in 0..6) counts[i] = seenDatesPerWeekday[i].size
        return (0..6).map { i ->
            val avg = if (counts[i] == 0) 0.0 else (totals[i] / 100.0) / counts[i]
            WeekdayPoint(dayOfWeek = i + 1, average = avg)
        }
    }

    private fun buildDailyPoints(
        from: LocalDate,
        to: LocalDate,
        expenses: List<com.qolve.fluyo.domain.model.Expense>,
    ): List<DailyPoint> {
        val byDay = expenses.groupBy { it.expenseDate }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val points = mutableListOf<DailyPoint>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            points += DailyPoint(cursor, byDay[cursor] ?: 0.0)
            cursor = cursor.plusDays(1)
        }
        return points
    }
}
