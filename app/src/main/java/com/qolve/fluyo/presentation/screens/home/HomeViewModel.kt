package com.qolve.fluyo.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.presentation.util.sanitizeDecimalInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val displayName: String? = null,
    /** Optional avatar URL — typically from Google sign-in metadata. Null falls back to initials. */
    val avatarUrl: String? = null,
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
    private val budgetExtraRepository: BudgetExtraRepository,
) : ViewModel() {

    // Two fields tracked independently — display name flips when the user updates their
    // profile, avatar URL only changes if they re-sign-in via a different provider. Splitting
    // them keeps the combine() below stable and avoids unnecessary re-emissions.
    private val displayName = MutableStateFlow<String?>(null)
    private val avatarUrl = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        expenseRepository.observeMonthlyBreakdown(),
        expenseRepository.observeRecentExpenses(),
        categoryRepository.observeCategories(),
        displayName,
        avatarUrl,
    ) { breakdown, expenses, categories, name, avatar ->
        HomeUiState(
            isLoading = false,
            displayName = name,
            avatarUrl = avatar,
            breakdown = breakdown,
            recentExpenses = expenses,
            categoriesById = categories.associateBy { it.id },
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
        val error: String? = null,
    ) {
        val monthExtrasTotal: Double get() = monthExtras.sumOf { it.amount }
    }

    val budgetDialog = MutableStateFlow(BudgetDialogState())

    init {
        refresh()
        viewModelScope.launch {
            val user = authRepository.currentUser().getOrNull() ?: return@launch
            displayName.value = user.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            avatarUrl.value = user.avatarUrl
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            categoryRepository.refresh()
            expenseRepository.refresh()
            isRefreshing.value = false
        }
    }

    // ─── Budget dialog (tap on the ring) ─────────────────────────────────────

    fun openBudgetDialog() {
        viewModelScope.launch {
            val base = authRepository.currentUser().getOrNull()?.monthlyBudget ?: 0.0
            budgetDialog.update {
                it.copy(
                    showBudget = true,
                    budgetInput = if (base % 1.0 == 0.0 && base > 0.0) {
                        base.toLong().toString()
                    } else if (base > 0.0) {
                        base.toString()
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
        val raw = budgetDialog.value.budgetInput.toDoubleOrNull() ?: return
        if (raw < 0.0) return
        budgetDialog.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            authRepository.updateProfile(monthlyBudget = raw, phoneNumber = null).fold(
                onSuccess = {
                    budgetDialog.update { it.copy(showBudget = false, isSaving = false, budgetInput = "") }
                    expenseRepository.refresh()
                },
                onFailure = { e ->
                    budgetDialog.update { it.copy(isSaving = false, error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    private fun reloadMonthExtras() {
        viewModelScope.launch {
            val extras = budgetExtraRepository
                .extrasForMonth(YearMonth.now())
                .getOrDefault(emptyList())
            budgetDialog.update { it.copy(monthExtras = extras) }
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
        val amount = budgetDialog.value.extraAmountInput.toDoubleOrNull() ?: return
        if (amount <= 0.0) return
        budgetDialog.update { it.copy(isSavingExtra = true, error = null) }
        viewModelScope.launch {
            budgetExtraRepository
                .addExtra(
                    amount,
                    budgetDialog.value.extraNoteInput.takeIf { it.isNotBlank() },
                    YearMonth.now(),
                )
                .fold(
                    onSuccess = {
                        budgetDialog.update {
                            it.copy(showExtra = false, extraAmountInput = "", extraNoteInput = "", isSavingExtra = false)
                        }
                        reloadMonthExtras()
                        expenseRepository.refresh()
                    },
                    onFailure = { e ->
                        budgetDialog.update { it.copy(isSavingExtra = false, error = e.localizedMessage ?: "Error") }
                    },
                )
        }
    }

    fun deleteExtra(extra: BudgetExtra) {
        viewModelScope.launch {
            budgetExtraRepository.deleteExtra(extra.id)
            reloadMonthExtras()
            expenseRepository.refresh()
        }
    }
}
