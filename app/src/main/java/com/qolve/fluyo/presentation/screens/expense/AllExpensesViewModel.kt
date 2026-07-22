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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.qolve.fluyo.domain.time.FluyoTime
import javax.inject.Inject

data class AllExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Full expense history ("Ver todo"). The repository pages the complete range so this
 * screen remains correct after the account grows beyond PostgREST's per-response cap.
 */
@HiltViewModel
class AllExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AllExpensesUiState())
    val state: StateFlow<AllExpensesUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { list ->
                _state.update { it.copy(categoriesById = list.associateBy { c -> c.id }) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val categoriesResult = categoryRepository.refresh()
            val expensesResult = expenseRepository.loadByDateRange(EPOCH_START, FluyoTime.today())
            val expenses = expensesResult.getOrNull()
            _state.update { current ->
                current.copy(
                    expenses = expenses ?: current.expenses,
                    hasLoaded = current.hasLoaded || expensesResult.isSuccess,
                    isLoading = false,
                    errorMessage = when {
                        expensesResult.isFailure -> "No se pudo cargar el historial"
                        categoriesResult.isFailure -> "No se pudieron cargar las categorías"
                        else -> null
                    },
                )
            }
        }
    }

    /** Kept for existing retry call sites. */
    fun load() = refresh()

    private companion object {
        // Well before any real Fluyo data; effectively "everything".
        val EPOCH_START: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
