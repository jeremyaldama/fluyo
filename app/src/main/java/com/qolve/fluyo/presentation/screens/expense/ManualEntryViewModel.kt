package com.qolve.fluyo.presentation.screens.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.usecase.RegisterExpenseUseCase
import com.qolve.fluyo.data.badge.BadgeEngine
import com.qolve.fluyo.presentation.events.AppEvent
import com.qolve.fluyo.presentation.events.AppEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ManualEntryUiState(
    val amountInput: String = "",
    val description: String = "",
    val selectedCategoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedOk: Boolean = false,
) {
    val parsedAmount: Double?
        get() = amountInput
            .replace(",", ".")
            .toDoubleOrNull()
            ?.takeIf { it > 0.0 }

    val canSave: Boolean
        get() = !isSaving && parsedAmount != null && selectedCategoryId != null
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val registerExpense: RegisterExpenseUseCase,
    private val appEvents: AppEvents,
    private val badgeEngine: BadgeEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(ManualEntryUiState())
    val state: StateFlow<ManualEntryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.refresh()
            categoryRepository.observeCategories().collect { list ->
                _state.update { it.copy(categories = list) }
            }
        }
    }

    fun onAmountChange(value: String) {
        // Allow digits, one comma or dot, max 2 decimals.
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }
        val normalized = cleaned.replace(',', '.')
        val parts = normalized.split('.')
        val trimmed = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
            parts.size == 2 -> parts[0] + "." + parts[1].take(2)
            else -> normalized
        }
        _state.update { it.copy(amountInput = trimmed) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
    }

    fun onCategorySelect(categoryId: String) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val current = _state.value
        val amount = current.parsedAmount ?: return
        val categoryId = current.selectedCategoryId ?: return

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = registerExpense(
                amount = amount,
                categoryId = categoryId,
                description = current.description.takeIf { it.isNotBlank() },
                expenseDate = current.date,
                source = ExpenseSource.MANUAL,
            )
            result.fold(
                onSuccess = { saved ->
                    appEvents.emit(
                        AppEvent.ExpenseSaved(saved.amount, AppEvent.ExpenseSaved.Source.MANUAL),
                    )
                    runCatching { badgeEngine.checkAfterExpense() }
                    _state.update { it.copy(isSaving = false, savedOk = true) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = e.localizedMessage ?: "Error")
                    }
                },
            )
        }
    }
}
