package com.qolve.fluyo.presentation.screens.scan

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.ocr.OcrService
import com.qolve.fluyo.data.ocr.SecureOcrImageImporter
import com.qolve.fluyo.data.local.SensitiveCacheCleaner
import com.qolve.fluyo.data.ocr.YapeParser
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.DetectedField
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.ParsedReceipt
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.usecase.RegisterExpenseUseCase
import com.qolve.fluyo.notifications.AchievementScheduler
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

data class OcrConfirmUiState(
    val imageUri: Uri? = null,
    val isProcessing: Boolean = true,
    val amountInput: String = "",
    val recipient: String = "",
    val description: String = "",
    val date: LocalDate = FluyoTime.today(),
    val selectedCategoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val categoryLoadError: String? = null,
    val autoDetected: Set<DetectedField> = emptySet(),
    val parseError: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedOk: Boolean = false,
) {
    val parsedAmount: MoneyAmount?
        get() = MoneyAmount.parse(amountInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }

    val canSave: Boolean
        get() = !isProcessing &&
            !isSaving &&
            parsedAmount != null &&
            selectedCategoryId != null &&
            isAllowedExpenseDate(date, FluyoTime.today())
}

@HiltViewModel
class OcrConfirmViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageImporter: SecureOcrImageImporter,
    private val ocrService: OcrService,
    private val yapeParser: YapeParser,
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val registerExpense: RegisterExpenseUseCase,
    private val appEvents: AppEvents,
    private val achievementScheduler: AchievementScheduler,
    private val sensitiveCacheCleaner: SensitiveCacheCleaner,
    private val sessionBoundary: SessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(OcrConfirmUiState())
    val state: StateFlow<OcrConfirmUiState> = _state.asStateFlow()
    private var pendingSourceUri: Uri? = null
    private var categoryRefreshJob: Job? = null
    private val pendingCreateRequest = StableMutationRequestStore(
        state = savedStateHandle,
        namespace = "ocr_expense_create",
    )

    init {
        val encoded: String? = savedStateHandle["uri"]
        val uri = encoded?.let { runCatching { Uri.decode(it).toUri() }.getOrNull() }

        // Observe cached categories immediately; a slow/offline refresh must not delay
        // an already usable category list or leave the confirmation screen unexplained.
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { list ->
                _state.update { current ->
                    current.copy(
                        categories = list,
                        isLoadingCategories = if (list.isNotEmpty()) false else current.isLoadingCategories,
                        categoryLoadError = if (list.isNotEmpty()) null else current.categoryLoadError,
                        // Pick the first default category as the initial selection if none chosen yet.
                        selectedCategoryId = current.selectedCategoryId
                            ?.takeIf { selected -> list.any { it.id == selected } }
                            ?: list.firstOrNull { it.isDefault }?.id
                            ?: list.firstOrNull()?.id,
                    )
                }
            }
        }
        retryCategories()

        if (uri != null) {
            pendingSourceUri = uri
            processImage(uri)
        } else {
            _state.update { it.copy(isProcessing = false, parseError = true) }
        }
    }

    private fun processImage(uri: Uri) {
        val sessionEpoch = sessionBoundary.snapshot()
        viewModelScope.launch {
            val imported = imageImporter.import(uri) {
                sessionBoundary.requireCurrent(sessionEpoch)
            }
            sessionBoundary.requireCurrent(sessionEpoch)
            val safeUri = imported.getOrNull()?.uri
            if (safeUri == null) {
                sensitiveCacheCleaner.deleteOwnedCapture(uri)
                pendingSourceUri = null
                savedStateHandle["uri"] = null
                _state.update {
                    it.copy(
                        imageUri = null,
                        isProcessing = false,
                        parseError = true,
                        errorMessage = "La imagen no es válida o supera el límite permitido",
                    )
                }
                return@launch
            }

            // The external/camera URI has crossed the trust boundary. From this point onward,
            // preview and OCR use only the bounded copy exposed by our own FileProvider.
            _state.update { it.copy(imageUri = safeUri) }
            savedStateHandle["uri"] = Uri.encode(safeUri.toString())
            if (uri != safeUri) sensitiveCacheCleaner.deleteOwnedCapture(uri)
            pendingSourceUri = null

            val recognition = ocrService.recognize(safeUri)
            sessionBoundary.requireCurrent(sessionEpoch)
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

    internal fun applyParsed(parsed: ParsedReceipt) {
        val parsedDate = parsed.date?.takeIf {
            isAllowedExpenseDate(it, FluyoTime.today())
        }
        _state.update { current ->
            current.copy(
                isProcessing = false,
                amountInput = parsed.amount?.let { formatAmountForInput(it) } ?: current.amountInput,
                recipient = parsed.recipient ?: current.recipient,
                date = parsedDate ?: current.date,
                // The voucher's message chip ("delicia") becomes the description prefill.
                description = parsed.note ?: current.description,
                autoDetected = if (parsedDate != null) {
                    parsed.detected
                } else {
                    parsed.detected - DetectedField.DATE
                },
                parseError = parsed.amount == null,
            )
        }
    }

    private fun formatAmountForInput(value: MoneyAmount): String =
        value.toBigDecimal()
            .stripTrailingZeros()
            .toPlainString()

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

    fun onDateChange(date: LocalDate) {
        if (!isAllowedExpenseDate(date, FluyoTime.today())) {
            _state.update {
                it.copy(errorMessage = "Elige una fecha entre el año 2000 y hoy")
            }
            return
        }
        _state.update {
            it.copy(
                date = date,
                autoDetected = it.autoDetected - DetectedField.DATE,
                errorMessage = null,
            )
        }
    }

    fun onCategorySelect(id: String) {
        _state.update { it.copy(selectedCategoryId = id) }
    }

    fun retryCategories() {
        if (categoryRefreshJob?.isActive == true) return
        _state.update { it.copy(isLoadingCategories = true, categoryLoadError = null) }
        categoryRefreshJob = viewModelScope.launch {
            categoryRepository.refresh().fold(
                onSuccess = {
                    _state.update { current ->
                        if (current.categories.isEmpty()) {
                            current.copy(
                                isLoadingCategories = false,
                                categoryLoadError = "No hay categorías disponibles",
                            )
                        } else {
                            current.copy(isLoadingCategories = false, categoryLoadError = null)
                        }
                    }
                },
                onFailure = {
                    _state.update { current ->
                        if (current.categories.isEmpty()) {
                            current.copy(
                                isLoadingCategories = false,
                                categoryLoadError = "No se pudieron cargar las categorías",
                            )
                        } else {
                            current.copy(isLoadingCategories = false)
                        }
                    }
                },
            )
        }
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
        val recipient = current.recipient.trim().takeIf { it.isNotEmpty() }
        val payload = arrayOf(
            amount.cents.toString(),
            categoryId,
            description,
            current.date.toString(),
            ExpenseSource.OCR.wire,
            recipient,
        )

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            var submittedRequestId: String? = null
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
            val result = when (resolution) {
                is PendingMutationResolution.Committed -> Result.success(resolution.value)
                is PendingMutationResolution.Ready -> {
                    submittedRequestId = resolution.requestId
                    registerExpense(
                        amount = amount,
                        categoryId = categoryId,
                        description = description,
                        expenseDate = current.date,
                        source = ExpenseSource.OCR,
                        recipient = recipient,
                        // A cache-backed content:// URI is device-local and transient;
                        // persisting it remotely produces a dead reference and retains data.
                        imageUrl = null,
                        requestId = resolution.requestId,
                    )
                }
            }
            result.fold(
                onSuccess = { saved ->
                    submittedRequestId?.let(pendingCreateRequest::complete)
                    // The financial write is complete. Surface success before any optional
                    // event or achievement side effect, and let WorkManager reconcile from
                    // authoritative server state even if this ViewModel is cleared now.
                    _state.update { it.copy(imageUri = null, isSaving = false, savedOk = true) }
                    sensitiveCacheCleaner.deleteOwnedCapture(current.imageUri)
                    runCatching { achievementScheduler.reconcileExpense() }
                    appEvents.emit(
                        AppEvent.ExpenseSaved(saved.amount, AppEvent.ExpenseSaved.Source.OCR),
                        sessionEpoch,
                    )
                },
                onFailure = {
                    _state.update {
                        it.copy(isSaving = false, errorMessage = "No se pudo guardar el gasto")
                    }
                },
            )
        }
    }

    override fun onCleared() {
        sensitiveCacheCleaner.deleteOwnedCapture(pendingSourceUri)
        sensitiveCacheCleaner.deleteOwnedCapture(_state.value.imageUri)
        super.onCleared()
    }
}
