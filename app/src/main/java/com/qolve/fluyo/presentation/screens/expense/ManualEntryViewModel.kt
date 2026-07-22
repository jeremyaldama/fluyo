package com.qolve.fluyo.presentation.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.usecase.RegisterExpenseUseCase
import com.qolve.fluyo.notifications.AchievementScheduler
import com.qolve.fluyo.data.voice.VoiceParser
import com.qolve.fluyo.presentation.events.AppEvent
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.presentation.util.StableMutationRequestStore
import com.qolve.fluyo.presentation.util.PendingMutationResolution
import com.qolve.fluyo.presentation.util.isAllowedExpenseDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.qolve.fluyo.domain.time.FluyoTime
import java.math.RoundingMode
import javax.inject.Inject

data class ManualEntryUiState(
    val amountInput: String = "",
    val description: String = "",
    val selectedCategoryId: String? = null,
    val date: LocalDate = FluyoTime.today(),
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedOk: Boolean = false,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isEditReady: Boolean = false,
    val loadErrorMessage: String? = null,
) {
    val parsedAmount: MoneyAmount?
        get() = MoneyAmount.parse(amountInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }

    val canSave: Boolean
        get() = !isSaving &&
            (!isEditing || (isEditReady && !isLoading)) &&
            parsedAmount != null &&
            selectedCategoryId != null &&
            isAllowedExpenseDate(date, FluyoTime.today())
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val registerExpense: RegisterExpenseUseCase,
    private val expenseRepository: ExpenseRepository,
    private val appEvents: AppEvents,
    private val achievementScheduler: AchievementScheduler,
    private val sessionBoundary: SessionBoundary,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pendingCreateRequest = StableMutationRequestStore(
        state = savedStateHandle,
        namespace = "manual_expense_create",
    )

    // Prefill args — set by voice entry (HU-05); empty for plain manual entry.
    private val source: ExpenseSource =
        ExpenseSource.fromWire(savedStateHandle.get<String>("src") ?: "manual")
    private val prefillAmount: String = savedStateHandle.get<String>("amount").orEmpty()
    private val prefillDesc: String = savedStateHandle.get<String>("desc").orEmpty()

    // Edit mode — set when the screen is opened from an existing expense row.
    private val expenseId: String? =
        savedStateHandle.get<String>("expenseId")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(
        ManualEntryUiState(
            isEditing = expenseId != null,
            isLoading = expenseId != null,
        ),
    )
    val state: StateFlow<ManualEntryUiState> = _state.asStateFlow()
    private var editLoadJob: Job? = null
    private var categoryRefreshJob: Job? = null
    private var editLoadGeneration = 0L
    private var hasUnsavedEdit = false

    init {
        if (prefillAmount.isNotBlank()) onAmountChange(prefillAmount)
        if (prefillDesc.isNotBlank()) onDescriptionChange(prefillDesc)
        expenseId?.let { loadForEdit(it, force = false) }

        // For voice, guess a category from the dictated text (HU-05). Applied once the
        // category list loads, and only if the user hasn't already picked one.
        val categoryHint =
            if (source == ExpenseSource.VOICE) VoiceParser.parse(prefillDesc).categoryHint else null

        viewModelScope.launch {
            categoryRepository.observeCategories().collect { list ->
                _state.update { current ->
                    val stillAvailable = current.selectedCategoryId?.takeIf { selected ->
                        list.isEmpty() || list.any { it.id == selected }
                    }
                    val guessed = if (stillAvailable == null && categoryHint != null) {
                        list.firstOrNull { it.name.equals(categoryHint, ignoreCase = true) }?.id
                    } else {
                        null
                    }
                    current.copy(
                        categories = list,
                        selectedCategoryId = stillAvailable ?: guessed,
                    )
                }
            }
        }
        refreshCategories()
    }

    private fun loadForEdit(id: String, force: Boolean) {
        if (!force && editLoadJob?.isActive == true) return
        editLoadJob?.cancel()
        val generation = ++editLoadGeneration
        _state.update {
            it.copy(isLoading = true, isEditReady = false, loadErrorMessage = null)
        }
        editLoadJob = viewModelScope.launch {
            expenseRepository.getById(id).fold(
                onSuccess = { expense ->
                    if (generation != editLoadGeneration) return@fold
                    if (expense == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isEditReady = false,
                                loadErrorMessage = "No encontramos este gasto o ya no tienes acceso",
                            )
                        }
                    } else {
                        hasUnsavedEdit = false
                        _state.update {
                            it.copy(
                                amountInput = expense.amount.toBigDecimal()
                                    .stripTrailingZeros().toPlainString(),
                                description = expense.description.orEmpty(),
                                selectedCategoryId = expense.categoryId,
                                date = expense.expenseDate,
                                isLoading = false,
                                isEditReady = true,
                                loadErrorMessage = null,
                            )
                        }
                    }
                },
                onFailure = {
                    if (generation != editLoadGeneration) return@fold
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isEditReady = false,
                            loadErrorMessage = "No se pudo cargar el gasto",
                        )
                    }
                },
            )
        }
    }

    /** Refreshes external edits on return without overwriting local, unsaved changes. */
    fun onResume() {
        refreshCategories()
        val id = expenseId ?: return
        if (!hasUnsavedEdit && !_state.value.savedOk) loadForEdit(id, force = false)
    }

    fun retryEditLoad() {
        val id = expenseId ?: return
        hasUnsavedEdit = false
        loadForEdit(id, force = true)
    }

    private fun refreshCategories() {
        if (categoryRefreshJob?.isActive == true) return
        categoryRefreshJob = viewModelScope.launch {
            categoryRepository.refresh().onFailure {
                if (_state.value.categories.isEmpty()) {
                    _state.update { current ->
                        current.copy(errorMessage = "No se pudieron cargar las categorías")
                    }
                }
            }
        }
    }

    fun onDateChange(value: LocalDate) {
        if (_state.value.isEditing && !_state.value.isEditReady) return
        if (!isAllowedExpenseDate(value, FluyoTime.today())) {
            _state.update { it.copy(errorMessage = "Elige una fecha entre el año 2000 y hoy") }
            return
        }
        markEditDirty()
        _state.update { it.copy(date = value, errorMessage = null) }
    }

    fun delete() {
        val id = expenseId ?: return
        if (!_state.value.isEditReady || _state.value.isSaving) return
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            expenseRepository.delete(id).fold(
                onSuccess = { _state.update { it.copy(isSaving = false, savedOk = true) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = "No se pudo eliminar el gasto")
                    }
                },
            )
        }
    }

    fun onAmountChange(value: String) {
        if (_state.value.isEditing && !_state.value.isEditReady) return
        // Allow digits, one comma or dot, max 2 decimals.
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }
        val normalized = cleaned.replace(',', '.')
        val parts = normalized.split('.')
        val trimmed = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
            parts.size == 2 -> parts[0] + "." + parts[1].take(2)
            else -> normalized
        }
        markEditDirty()
        _state.update { it.copy(amountInput = trimmed) }
    }

    fun onDescriptionChange(value: String) {
        if (_state.value.isEditing && !_state.value.isEditReady) return
        markEditDirty()
        _state.update { it.copy(description = value) }
    }

    fun onCategorySelect(categoryId: String) {
        if (_state.value.isEditing && !_state.value.isEditReady) return
        markEditDirty()
        _state.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val sessionEpoch = sessionBoundary.snapshot()
        val current = _state.value
        if (!current.canSave || current.savedOk) return
        val amount = current.parsedAmount ?: return
        val categoryId = current.selectedCategoryId ?: return
        val description = current.description.trim().takeIf { it.isNotEmpty() }
        val payload = arrayOf(
            amount.cents.toString(),
            categoryId,
            description,
            current.date.toString(),
            source.wire,
        )

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            var submittedRequestId: String? = null
            val result = if (expenseId != null) {
                // Edit: silent update — no celebration event, no badge re-check.
                expenseRepository.update(
                    id = expenseId,
                    amount = amount,
                    categoryId = categoryId,
                    description = description,
                    expenseDate = current.date,
                )
            } else {
                val resolution = pendingCreateRequest.resolve(
                    *payload,
                    findCommitted = expenseRepository::findCreatedByRequestId,
                ).getOrElse {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "No se pudo verificar el intento anterior",
                        )
                    }
                    return@launch
                }
                when (resolution) {
                    is PendingMutationResolution.Committed -> Result.success(resolution.value)
                    is PendingMutationResolution.Ready -> {
                        submittedRequestId = resolution.requestId
                        registerExpense(
                            amount = amount,
                            categoryId = categoryId,
                            description = description,
                            expenseDate = current.date,
                            source = source,
                            requestId = resolution.requestId,
                        )
                    }
                }
            }
            result.fold(
                onSuccess = { saved ->
                    if (expenseId == null) {
                        submittedRequestId?.let(pendingCreateRequest::complete)
                        // Persistence is already complete. UI success must not wait on optional
                        // event/badge reconciliation, which may fail or be cancelled independently.
                        _state.update { it.copy(isSaving = false, savedOk = true) }
                        appEvents.emit(
                            AppEvent.ExpenseSaved(saved.amount, AppEvent.ExpenseSaved.Source.MANUAL),
                            sessionEpoch,
                        )
                        runCatching { achievementScheduler.reconcileExpense() }
                    } else {
                        _state.update { it.copy(isSaving = false, savedOk = true) }
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = "No se pudo guardar el gasto")
                    }
                },
            )
        }
    }

    private fun markEditDirty() {
        if (_state.value.isEditing && _state.value.isEditReady) hasUnsavedEdit = true
    }
}
