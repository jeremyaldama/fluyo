package com.qolve.fluyo.presentation.screens.scan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.ocr.OcrService
import com.qolve.fluyo.data.ocr.YapeParser
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.DetectedField
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.ParsedReceipt
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

data class OcrConfirmUiState(
    val imageUri: Uri? = null,
    val isProcessing: Boolean = true,
    val amountInput: String = "",
    val recipient: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val selectedCategoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val autoDetected: Set<DetectedField> = emptySet(),
    val parseError: Boolean = false,
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
        get() = !isProcessing && !isSaving && parsedAmount != null && selectedCategoryId != null
}

@HiltViewModel
class OcrConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ocrService: OcrService,
    private val yapeParser: YapeParser,
    private val categoryRepository: CategoryRepository,
    private val registerExpense: RegisterExpenseUseCase,
    private val appEvents: AppEvents,
    private val badgeEngine: BadgeEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(OcrConfirmUiState())
    val state: StateFlow<OcrConfirmUiState> = _state.asStateFlow()

    init {
        val encoded: String? = savedStateHandle["uri"]
        val uri = encoded?.let { runCatching { Uri.parse(Uri.decode(it)) }.getOrNull() }
        _state.update { it.copy(imageUri = uri) }

        viewModelScope.launch {
            categoryRepository.refresh()
            categoryRepository.observeCategories().collect { list ->
                _state.update { current ->
                    current.copy(
                        categories = list,
                        // Pick the first default category as the initial selection if none chosen yet.
                        selectedCategoryId = current.selectedCategoryId
                            ?: list.firstOrNull { it.isDefault }?.id
                            ?: list.firstOrNull()?.id,
                    )
                }
            }
        }

        if (uri != null) processImage(uri) else {
            _state.update { it.copy(isProcessing = false, parseError = true) }
        }
    }

    private fun processImage(uri: Uri) {
        viewModelScope.launch {
            val recognition = ocrService.recognize(uri)
            recognition.fold(
                onSuccess = { raw ->
                    val parsed = yapeParser.parse(raw)
                    applyParsed(parsed)
                },
                onFailure = {
                    _state.update { it.copy(isProcessing = false, parseError = true) }
                },
            )
        }
    }

    private fun applyParsed(parsed: ParsedReceipt) {
        _state.update { current ->
            current.copy(
                isProcessing = false,
                amountInput = parsed.amount?.let { formatAmountForInput(it) } ?: current.amountInput,
                recipient = parsed.recipient ?: current.recipient,
                date = parsed.date ?: current.date,
                autoDetected = parsed.detected,
                parseError = parsed.amount == null,
            )
        }
    }

    private fun formatAmountForInput(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            "%.2f".format(rounded)
        }
    }

    fun onAmountChange(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val parts = cleaned.split('.')
        val trimmed = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
            parts.size == 2 -> parts[0] + "." + parts[1].take(2)
            else -> cleaned
        }
        _state.update {
            it.copy(
                amountInput = trimmed,
                autoDetected = it.autoDetected - DetectedField.AMOUNT,
                parseError = false,
            )
        }
    }

    fun onRecipientChange(value: String) {
        _state.update {
            it.copy(recipient = value, autoDetected = it.autoDetected - DetectedField.RECIPIENT)
        }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
    }

    fun onCategorySelect(id: String) {
        _state.update { it.copy(selectedCategoryId = id) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val current = _state.value
        val amount = current.parsedAmount ?: return
        val categoryId = current.selectedCategoryId ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = registerExpense(
                amount = amount,
                categoryId = categoryId,
                description = current.description.takeIf { it.isNotBlank() },
                expenseDate = current.date,
                source = ExpenseSource.OCR,
                recipient = current.recipient.takeIf { it.isNotBlank() },
                imageUrl = current.imageUri?.toString(),
            )
            result.fold(
                onSuccess = { saved ->
                    appEvents.emit(
                        AppEvent.ExpenseSaved(saved.amount, AppEvent.ExpenseSaved.Source.OCR),
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
