package com.qolve.fluyo.presentation.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.sumMoney
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.presentation.util.sanitizeDecimalInput
import com.qolve.fluyo.presentation.util.PendingMutationResolution
import com.qolve.fluyo.presentation.util.StableMutationRequestStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.YearMonth
import com.qolve.fluyo.domain.time.FluyoTime
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class HomeUiState(
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val displayName: String? = null,
    /** Optional avatar URL — typically from Google sign-in metadata. Null falls back to initials. */
    val avatarUrl: String? = null,
    val breakdown: MonthlyBreakdown = MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO),
    val recentExpenses: List<Expense> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val budgetExtraRepository: BudgetExtraRepository,
    private val sessionBoundary: SessionBoundary,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pendingExtraRequests = StableMutationRequestStore(
        savedStateHandle,
        namespace = "home_budget_extra",
    )

    // Two fields tracked independently — display name flips when the user updates their
    // profile, avatar URL only changes if they re-sign-in via a different provider. Splitting
    // them keeps the combine() below stable and avoids unnecessary re-emissions.
    private val displayName = MutableStateFlow<String?>(null)
    private val avatarUrl = MutableStateFlow<String?>(null)

    private data class LoadState(
        val hasLoaded: Boolean = false,
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
    )

    private val loadState = MutableStateFlow(LoadState())
    private var refreshJob: Job? = null
    private val pendingExtraDeletes = ConcurrentHashMap.newKeySet<String>()

    private val contentState = combine(
        expenseRepository.observeMonthlyBreakdown(),
        expenseRepository.observeRecentExpenses(),
        categoryRepository.observeCategories(),
        displayName,
        avatarUrl,
    ) { breakdown, expenses, categories, name, avatar ->
        HomeUiState(
            displayName = name,
            avatarUrl = avatar,
            breakdown = breakdown,
            recentExpenses = expenses,
            categoriesById = categories.associateBy { it.id },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(contentState, loadState) { content, load ->
        content.copy(
            hasLoaded = load.hasLoaded,
            isLoading = load.isLoading && !load.hasLoaded,
            errorMessage = load.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** True while a user-initiated pull-to-refresh is in flight. */
    val isRefreshing = MutableStateFlow(false)

    /**
     * Budget-edit dialog state, hosted here so tapping the ring edits in place
     * (no trip to Perfil). Separate flow — the uiState combine is already at the
     * 5-flow overload limit.
     */
    data class BudgetDialogState(
        val showBudget: Boolean = false,
        val budgetInput: String = "",
        val isSaving: Boolean = false,
        val showExtra: Boolean = false,
        val extraAmountInput: String = "",
        val extraNoteInput: String = "",
        val isSavingExtra: Boolean = false,
        val monthExtras: List<BudgetExtra> = emptyList(),
        val deletingExtraIds: Set<String> = emptySet(),
        val error: String? = null,
    ) {
        val monthExtrasTotal: MoneyAmount get() = monthExtras.map { it.amount }.sumMoney()
    }

    val budgetDialog = MutableStateFlow(BudgetDialogState())

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hadContent = loadState.value.hasLoaded
            loadState.update { it.copy(isLoading = true, errorMessage = null) }
            isRefreshing.value = hadContent

            val categoryResult = categoryRepository.refresh()
            val expenseResult = expenseRepository.refresh()
            val userResult = authRepository.currentUser()
            userResult.getOrNull()?.let { user ->
                displayName.value = user.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                avatarUrl.value = user.avatarUrl
            }

            val failure = categoryResult.exceptionOrNull()
                ?: expenseResult.exceptionOrNull()
                ?: userResult.exceptionOrNull()
            loadState.value = LoadState(
                hasLoaded = hadContent || failure == null,
                isLoading = false,
                errorMessage = failure?.let { "No se pudo actualizar el inicio" },
            )
            isRefreshing.value = false
        }
    }

    // ─── Budget dialog (tap on the ring) ─────────────────────────────────────

    fun openBudgetDialog() {
        viewModelScope.launch {
            val base = authRepository.currentUser().getOrNull()?.monthlyBudget ?: MoneyAmount.ZERO
            budgetDialog.update {
                it.copy(
                    showBudget = true,
                    budgetInput = if (base > MoneyAmount.ZERO) {
                        base.toBigDecimal().stripTrailingZeros().toPlainString()
                    } else {
                        ""
                    },
                )
            }
            reloadMonthExtras()
        }
    }

    fun closeBudgetDialog() {
        budgetDialog.update { it.copy(showBudget = false, budgetInput = "", isSaving = false) }
    }

    fun onBudgetInputChange(value: String) {
        budgetDialog.update { it.copy(budgetInput = sanitizeDecimalInput(value)) }
    }

    fun saveBudget() {
        val raw = MoneyAmount.parse(budgetDialog.value.budgetInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it >= MoneyAmount.ZERO }
            ?: return
        budgetDialog.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            authRepository.updateProfile(monthlyBudget = raw).fold(
                onSuccess = {
                    budgetDialog.update { it.copy(showBudget = false, isSaving = false, budgetInput = "") }
                    expenseRepository.refresh()
                },
                onFailure = { e ->
                    budgetDialog.update { it.copy(isSaving = false, error = "No se pudo actualizar el presupuesto") }
                },
            )
        }
    }

    private fun reloadMonthExtras(expectedEpoch: Long = sessionBoundary.snapshot()) {
        viewModelScope.launch {
            budgetExtraRepository.extrasForMonth(FluyoTime.currentMonth()).fold(
                onSuccess = { extras ->
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        budgetDialog.update { it.copy(monthExtras = extras) }
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(expectedEpoch) {
                        budgetDialog.update {
                            it.copy(error = "No se pudieron cargar los ingresos del mes")
                        }
                    }
                },
            )
        }
    }

    fun openExtraDialog() {
        budgetDialog.update { it.copy(showExtra = true, extraAmountInput = "", extraNoteInput = "") }
    }

    fun closeExtraDialog() {
        budgetDialog.update {
            it.copy(showExtra = false, extraAmountInput = "", extraNoteInput = "", isSavingExtra = false)
        }
    }

    fun onExtraAmountChange(value: String) {
        budgetDialog.update { it.copy(extraAmountInput = sanitizeDecimalInput(value)) }
    }

    fun onExtraNoteChange(value: String) {
        budgetDialog.update { it.copy(extraNoteInput = value.take(60)) }
    }

    fun saveExtra() {
        if (budgetDialog.value.isSavingExtra) return
        val amount = MoneyAmount.parse(budgetDialog.value.extraAmountInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }
            ?: return
        val note = budgetDialog.value.extraNoteInput.trim().takeIf { it.isNotEmpty() }
        val month = FluyoTime.currentMonth()
        val payload = arrayOf(
            amount.cents.toString(),
            note,
            month.toString(),
        )
        budgetDialog.update { it.copy(isSavingExtra = true, error = null) }
        viewModelScope.launch {
            var submittedRequestId: String? = null
            val resolution = pendingExtraRequests.resolve(
                *payload,
                findCommitted = budgetExtraRepository::findCreatedByRequestId,
            ).getOrElse {
                budgetDialog.update { state ->
                    state.copy(
                        isSavingExtra = false,
                        error = "No se pudo verificar el intento anterior",
                    )
                }
                return@launch
            }
            val result = when (resolution) {
                is PendingMutationResolution.Committed -> Result.success(resolution.value)
                is PendingMutationResolution.Ready -> {
                    submittedRequestId = resolution.requestId
                    budgetExtraRepository.addExtra(
                        amount,
                        note,
                        month,
                        resolution.requestId,
                    )
                }
            }
            result.fold(
                onSuccess = {
                    submittedRequestId?.let(pendingExtraRequests::complete)
                    budgetDialog.update {
                        it.copy(showExtra = false, extraAmountInput = "", extraNoteInput = "", isSavingExtra = false)
                    }
                    reloadMonthExtras()
                    expenseRepository.refresh()
                },
                onFailure = { e ->
                    budgetDialog.update { it.copy(isSavingExtra = false, error = "No se pudo agregar el ingreso") }
                },
            )
        }
    }

    fun deleteExtra(extra: BudgetExtra) {
        if (!pendingExtraDeletes.add(extra.id)) return
        val expectedEpoch = sessionBoundary.snapshot()
        if (!sessionBoundary.runIfCurrent(expectedEpoch) {
                budgetDialog.update {
                    it.copy(
                        deletingExtraIds = it.deletingExtraIds + extra.id,
                        error = null,
                    )
                }
            }
        ) {
            pendingExtraDeletes.remove(extra.id)
            return
        }

        viewModelScope.launch {
            try {
                budgetExtraRepository.deleteExtra(extra.id).fold(
                    onSuccess = {
                        if (sessionBoundary.runIfCurrent(expectedEpoch) {
                                // Reflect the confirmed delete immediately; the reload below
                                // remains authoritative and repairs any concurrent device write.
                                budgetDialog.update { state ->
                                    state.copy(
                                        monthExtras = state.monthExtras.filterNot { it.id == extra.id },
                                        error = null,
                                    )
                                }
                            }
                        ) {
                            reloadMonthExtras(expectedEpoch)
                            expenseRepository.refresh()
                        }
                    },
                    onFailure = {
                        sessionBoundary.runIfCurrent(expectedEpoch) {
                            budgetDialog.update { state ->
                                state.copy(error = "No se pudo eliminar el ingreso")
                            }
                        }
                    },
                )
            } finally {
                pendingExtraDeletes.remove(extra.id)
                sessionBoundary.runIfCurrent(expectedEpoch) {
                    budgetDialog.update { state ->
                        state.copy(deletingExtraIds = state.deletingExtraIds - extra.id)
                    }
                }
            }
        }
    }

}
