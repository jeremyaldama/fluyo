package com.qolve.fluyo.presentation.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.usecase.RegisterExpenseUseCase
import com.qolve.fluyo.data.badge.BadgeEngine
import com.qolve.fluyo.data.voice.VoiceParser
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
    val isEditing: Boolean = false,
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
    private val expenseRepository: ExpenseRepository,
    private val appEvents: AppEvents,
    private val badgeEngine: BadgeEngine,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Prefill args — set by voice entry (HU-05); empty for plain manual entry.
    private val source: ExpenseSource =
        ExpenseSource.fromWire(savedStateHandle.get<String>("src") ?: "manual")
    private val prefillAmount: String = savedStateHandle.get<String>("amount").orEmpty()
    private val prefillDesc: String = savedStateHandle.get<String>("desc").orEmpty()

    // Edit mode — set when the screen is opened from an existing expense row.
    private val expenseId: String? =
        savedStateHandle.get<String>("expenseId")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(ManualEntryUiState(isEditing = expenseId != null))
    val state: StateFlow<ManualEntryUiState> = _state.asStateFlow()

    init {
        if (prefillAmount.isNotBlank()) onAmountChange(prefillAmount)
        if (prefillDesc.isNotBlank()) onDescriptionChange(prefillDesc)
        expenseId?.let { loadForEdit(it) }

        // For voice, guess a category from the dictated text (HU-05). Applied once the
        // category list loads, and only if the user hasn't already picked one.
        val categoryHint =
            if (source == ExpenseSource.VOICE) VoiceParser.parse(prefillDesc).categoryHint else null

        viewModelScope.launch {
            categoryRepository.refresh()
            categoryRepository.observeCategories().collect { list ->
                _state.update { current ->
                    val guessed = if (current.selectedCategoryId == null && categoryHint != null) {
                        list.firstOrNull { it.name.equals(categoryHint, ignoreCase = true) }?.id
                    } else {
                        null
                    }
                    current.copy(
                        categories = list,
                        selectedCategoryId = current.selectedCategoryId ?: guessed,
                    )
                }
            }
        }
    }

    private fun loadForEdit(id: String) {
        viewModelScope.launch {
            expenseRepository.getById(id).getOrNull()?.let { expense ->
                _state.update {
                    it.copy(
                        amountInput = java.math.BigDecimal.valueOf(expense.amount)
                            .stripTrailingZeros().toPlainString(),
                        description = expense.description.orEmpty(),
                        selectedCategoryId = expense.categoryId ?: it.selectedCategoryId,
                        date = expense.expenseDate,
                    )
                }
            }
        }
    }

    fun onDateChange(value: LocalDate) {
        _state.update { it.copy(date = value) }
    }

    fun delete() {
        val id = expenseId ?: return
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            expenseRepository.delete(id).fold(
                onSuccess = { _state.update { it.copy(isSaving = false, savedOk = true) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = e.localizedMessage ?: "Error")
                    }
                },
            )
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
            val result = if (expenseId != null) {
                // Edit: silent update — no celebration event, no badge re-check.
                expenseRepository.update(
                    id = expenseId,
                    amount = amount,
                    categoryId = categoryId,
                    description = current.description.takeIf { it.isNotBlank() },
                    expenseDate = current.date,
                )
            } else {
                registerExpense(
                    amount = amount,
                    categoryId = categoryId,
                    description = current.description.takeIf { it.isNotBlank() },
                    expenseDate = current.date,
                    source = source,
                )
            }
            result.fold(
                onSuccess = { saved ->
                    if (expenseId == null) {
                        appEvents.emit(
                            AppEvent.ExpenseSaved(saved.amount, AppEvent.ExpenseSaved.Source.MANUAL),
                        )
                        runCatching { badgeEngine.checkAfterExpense() }
                    }
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
