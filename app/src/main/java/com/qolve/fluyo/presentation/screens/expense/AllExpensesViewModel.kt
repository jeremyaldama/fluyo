package com.qolve.fluyo.presentation.screens.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AllExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val isLoading: Boolean = true,
)

/**
 * Full expense history ("Ver todo"). Loads the complete range in one query — for the
 * target user (a student tracking daily spending) even a year of data is a few hundred
 * rows, well below where paging would earn its complexity.
 */
@HiltViewModel
class AllExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AllExpensesUiState())
    val state: StateFlow<AllExpensesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.refresh()
            categoryRepository.observeCategories().collect { list ->
                _state.update { it.copy(categoriesById = list.associateBy { c -> c.id }) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val expenses = expenseRepository
                .loadByDateRange(EPOCH_START, LocalDate.now())
                .getOrDefault(emptyList())
            _state.update { it.copy(expenses = expenses, isLoading = false) }
        }
    }

    private companion object {
        // Well before any real Fluyo data; effectively "everything".
        val EPOCH_START: LocalDate = LocalDate.of(2020, 1, 1)
    }
}
