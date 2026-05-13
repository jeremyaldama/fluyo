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

enum class StatsPeriod { WEEK, MONTH }

private data class DateRange(val from: LocalDate, val to: LocalDate)

private fun StatsPeriod.currentRange(today: LocalDate = LocalDate.now()): DateRange = when (this) {
    StatsPeriod.WEEK -> DateRange(today.minusDays(6), today)
    StatsPeriod.MONTH -> DateRange(today.with(TemporalAdjusters.firstDayOfMonth()), today)
}

private fun StatsPeriod.previousRange(today: LocalDate = LocalDate.now()): DateRange = when (this) {
    StatsPeriod.WEEK -> DateRange(today.minusDays(13), today.minusDays(7))
    StatsPeriod.MONTH -> {
        val firstOfThis = today.with(TemporalAdjusters.firstDayOfMonth())
        val lastOfPrev = firstOfThis.minusDays(1)
        val firstOfPrev = lastOfPrev.with(TemporalAdjusters.firstDayOfMonth())
        DateRange(firstOfPrev, lastOfPrev)
    }
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val isLoading: Boolean = true,
    val total: Double = 0.0,
    val previousTotal: Double = 0.0,
    val summaries: List<CategorySummary> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Signed percent delta vs previous period; null when previous period has no data. */
    val deltaPct: Float?
        get() = if (previousTotal <= 0.0) null
        else (((total - previousTotal) / previousTotal) * 100.0).toFloat()

    val isUnderPrevious: Boolean get() = (deltaPct ?: 0f) < 0f
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

            _state.update {
                it.copy(
                    isLoading = false,
                    total = currentExpenses.sumOf { e -> e.amount },
                    previousTotal = previousExpenses.sumOf { e -> e.amount },
                    summaries = summaries,
                )
            }
        }
    }
}
