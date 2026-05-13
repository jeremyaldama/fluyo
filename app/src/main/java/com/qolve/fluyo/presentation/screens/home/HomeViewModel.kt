package com.qolve.fluyo.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val displayName: String? = null,
    val breakdown: MonthlyBreakdown = MonthlyBreakdown(0.0, 0.0),
    val recentExpenses: List<Expense> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val displayName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        expenseRepository.observeMonthlyBreakdown(),
        expenseRepository.observeRecentExpenses(),
        categoryRepository.observeCategories(),
        displayName,
    ) { breakdown, expenses, categories, name ->
        HomeUiState(
            isLoading = false,
            displayName = name,
            breakdown = breakdown,
            recentExpenses = expenses,
            categoriesById = categories.associateBy { it.id },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refresh()
        viewModelScope.launch {
            authRepository.currentUser().getOrNull()?.displayName?.let { full ->
                displayName.value = full.trim().split(" ").firstOrNull()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            categoryRepository.refresh()
            expenseRepository.refresh()
        }
    }
}
