package com.qolve.fluyo.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.model.UserLevel
import com.qolve.fluyo.domain.model.UserLevelCatalog
import android.net.Uri
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.notifications.NudgeOneShot
import com.qolve.fluyo.notifications.NudgeScheduler
import com.qolve.fluyo.presentation.util.CsvExporter
import com.qolve.fluyo.presentation.util.CurrencyState
import com.qolve.fluyo.presentation.util.SUPPORTED_CURRENCIES
import com.qolve.fluyo.presentation.util.sanitizeDecimalInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val badges: List<Badge> = emptyList(),
    /** Current day-streak of registering an expense (ending today). 0 when broken. */
    val streak: Int = 0,
    val showBudgetDialog: Boolean = false,
    val budgetInput: String = "",
    val isSavingBudget: Boolean = false,
    /** "Ingreso extra del mes" dialog state. */
    val showExtraDialog: Boolean = false,
    val extraAmountInput: String = "",
    val extraNoteInput: String = "",
    val isSavingExtra: Boolean = false,
    val monthExtras: List<BudgetExtra> = emptyList(),
    /** Phone-edit dialog state. Mirrors the budget-dialog pattern for consistency. */
    val showPhoneDialog: Boolean = false,
    val phoneInput: String = "",
    val isSavingPhone: Boolean = false,
    /** Currency-picker dialog state. */
    val showCurrencyDialog: Boolean = false,
    val currencyInput: String = "PEN",
    val isSavingCurrency: Boolean = false,
    /** Delete-account confirmation state. */
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: String get() = user?.currency ?: "PEN"
    val monthExtrasTotal: Double get() = monthExtras.sumOf { it.amount }
    val totalPoints: Int get() = badges.sumOf { it.type.points }
    val currentLevel: UserLevel get() = UserLevelCatalog.levelFor(totalPoints)
    val unlockedTypes: Set<BadgeType> get() = badges.map { it.type }.toSet()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetExtraRepository: BudgetExtraRepository,
    private val nudgeScheduler: NudgeScheduler,
    private val nudgeOneShot: NudgeOneShot,
    private val currencyState: CurrencyState,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val userState = MutableStateFlow<User?>(null)
    private val streakState = MutableStateFlow(0)
    private val sheet = MutableStateFlow(SheetState())

    /** One-shot CSV export results — the screen collects this and fires the share sheet. */
    private val csvExports = Channel<Uri>(Channel.BUFFERED)
    val csvExportEvents: Flow<Uri> = csvExports.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = combine(
        userState,
        badgeRepository.observeBadges(),
        streakState,
        sheet,
    ) { user, badges, streak, s ->
        ProfileUiState(
            isLoading = user == null,
            user = user,
            badges = badges,
            streak = streak,
            showBudgetDialog = s.showBudget,
            budgetInput = s.budgetInput,
            isSavingBudget = s.savingBudget,
            showExtraDialog = s.showExtra,
            extraAmountInput = s.extraAmountInput,
            extraNoteInput = s.extraNoteInput,
            isSavingExtra = s.savingExtra,
            monthExtras = s.monthExtras,
            showPhoneDialog = s.showPhone,
            phoneInput = s.phoneInput,
            isSavingPhone = s.savingPhone,
            showCurrencyDialog = s.showCurrency,
            currencyInput = s.currencyInput,
            isSavingCurrency = s.savingCurrency,
            showDeleteDialog = s.showDelete,
            isDeleting = s.deleting,
            errorMessage = s.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            badgeRepository.refresh()
            userState.value = authRepository.currentUser().getOrNull()
            // Streak fetch is independent from the user load; failure leaves streak at 0
            // and the UI shows the "Empieza tu racha hoy" empty-state caption.
            streakState.value = runCatching { expenseRepository.currentStreak() }.getOrDefault(0)
        }
    }

    fun openBudgetDialog() {
        sheet.update {
            it.copy(
                showBudget = true,
                budgetInput = formatBudgetForInput(userState.value?.monthlyBudget ?: 0.0),
            )
        }
        reloadMonthExtras()
    }

    fun closeBudgetDialog() {
        sheet.update { it.copy(showBudget = false, budgetInput = "", savingBudget = false) }
    }

    fun onBudgetInputChange(value: String) {
        sheet.update { it.copy(budgetInput = sanitizeDecimalInput(value)) }
    }

    fun saveBudget() {
        val raw = sheet.value.budgetInput.toDoubleOrNull() ?: return
        if (raw < 0.0) return
        sheet.update { it.copy(savingBudget = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.updateProfile(monthlyBudget = raw, phoneNumber = null)
            result.fold(
                onSuccess = { updated ->
                    userState.value = updated
                    sheet.update { it.copy(showBudget = false, savingBudget = false, budgetInput = "") }
                    // Re-query the breakdown so the Home ring reflects the new base at once.
                    expenseRepository.refresh()
                },
                onFailure = { e ->
                    sheet.update {
                        it.copy(savingBudget = false, error = e.localizedMessage ?: "Error")
                    }
                },
            )
        }
    }

    // ─── "Ingreso extra del mes" (adds to the current month only) ───────────

    private fun reloadMonthExtras() {
        viewModelScope.launch {
            val extras = budgetExtraRepository
                .extrasForMonth(YearMonth.now())
                .getOrDefault(emptyList())
            sheet.update { it.copy(monthExtras = extras) }
        }
    }

    fun openExtraDialog() {
        sheet.update { it.copy(showExtra = true, extraAmountInput = "", extraNoteInput = "") }
    }

    fun closeExtraDialog() {
        sheet.update { it.copy(showExtra = false, extraAmountInput = "", extraNoteInput = "", savingExtra = false) }
    }

    fun onExtraAmountChange(value: String) {
        sheet.update { it.copy(extraAmountInput = sanitizeDecimalInput(value)) }
    }

    fun onExtraNoteChange(value: String) {
        sheet.update { it.copy(extraNoteInput = value.take(60)) }
    }

    fun saveExtra() {
        val amount = sheet.value.extraAmountInput.toDoubleOrNull() ?: return
        if (amount <= 0.0) return
        sheet.update { it.copy(savingExtra = true, error = null) }
        viewModelScope.launch {
            budgetExtraRepository
                .addExtra(amount, sheet.value.extraNoteInput.takeIf { it.isNotBlank() }, YearMonth.now())
                .fold(
                    onSuccess = {
                        sheet.update {
                            it.copy(showExtra = false, extraAmountInput = "", extraNoteInput = "", savingExtra = false)
                        }
                        reloadMonthExtras()
                        expenseRepository.refresh()
                    },
                    onFailure = { e ->
                        sheet.update { it.copy(savingExtra = false, error = e.localizedMessage ?: "Error") }
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

    fun consumeError() {
        sheet.update { it.copy(error = null) }
    }

    // ─── Phone-edit dialog (mirrors the budget-dialog plumbing) ─────────────
    //
    // Phone numbers serve two roles for Fluyo: (1) they're the join key the WhatsApp bot
    // uses to attribute incoming messages to a Supabase user, so changing it can detach the
    // bot binding; (2) they're optional — we never demand one from email/password users.
    // The dialog is intentionally low-friction: digits only, max 15 chars, no validation
    // beyond that (we don't try to detect carriers or country codes here).

    fun openPhoneDialog() {
        sheet.update {
            it.copy(showPhone = true, phoneInput = userState.value?.phoneNumber.orEmpty())
        }
    }

    fun closePhoneDialog() {
        sheet.update { it.copy(showPhone = false, phoneInput = "", savingPhone = false) }
    }

    fun onPhoneInputChange(value: String) {
        sheet.update { it.copy(phoneInput = value.filter { c -> c.isDigit() }.take(15)) }
    }

    fun savePhone() {
        val raw = sheet.value.phoneInput.trim()
        // Empty input clears the phone (sends null) — both writes go through updateProfile.
        sheet.update { it.copy(savingPhone = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.updateProfile(
                monthlyBudget = null,
                phoneNumber = raw.ifBlank { null },
            )
            result.fold(
                onSuccess = { updated ->
                    userState.value = updated
                    sheet.update { it.copy(showPhone = false, savingPhone = false, phoneInput = "") }
                },
                onFailure = { e ->
                    sheet.update { it.copy(savingPhone = false, error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    // ─── Currency picker (HU-11) ─────────────────────────────────────────────
    fun openCurrencyDialog() {
        sheet.update {
            it.copy(showCurrency = true, currencyInput = userState.value?.currency ?: "PEN")
        }
    }

    fun closeCurrencyDialog() {
        sheet.update { it.copy(showCurrency = false, savingCurrency = false) }
    }

    fun onCurrencySelect(code: String) {
        sheet.update { it.copy(currencyInput = code) }
    }

    fun saveCurrency() {
        val code = sheet.value.currencyInput.takeIf { it in SUPPORTED_CURRENCIES } ?: return
        sheet.update { it.copy(savingCurrency = true, error = null) }
        viewModelScope.launch {
            authRepository.updateProfile(currency = code).fold(
                onSuccess = { updated ->
                    userState.value = updated
                    currencyState.set(updated.currency)
                    sheet.update { it.copy(showCurrency = false, savingCurrency = false) }
                },
                onFailure = { e ->
                    sheet.update { it.copy(savingCurrency = false, error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    // ─── CSV export (HU-11) ──────────────────────────────────────────────────
    fun exportCsv() {
        viewModelScope.launch {
            runCatching {
                val expenses = expenseRepository
                    .loadByDateRange(LocalDate.of(2000, 1, 1), LocalDate.now())
                    .getOrDefault(emptyList())
                val names = categoryRepository.observeCategories().first()
                    .associate { it.id to it.name }
                val code = userState.value?.currency ?: "PEN"
                csvExporter.export(expenses, names, code)
            }.fold(
                onSuccess = { uri -> csvExports.send(uri) },
                onFailure = { e ->
                    sheet.update { it.copy(error = e.localizedMessage ?: "No se pudo exportar") }
                },
            )
        }
    }

    // ─── Delete account (HU-11) ──────────────────────────────────────────────
    fun openDeleteDialog() {
        sheet.update { it.copy(showDelete = true) }
    }

    fun closeDeleteDialog() {
        sheet.update { it.copy(showDelete = false, deleting = false) }
    }

    fun deleteAccount() {
        sheet.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            // On success the auth state flips to SignedOut and RootViewModel routes to login.
            authRepository.deleteAccount().onFailure { e ->
                sheet.update { it.copy(deleting = false, error = e.localizedMessage ?: "Error") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Manual smoke-test trigger: enqueues a one-time nudge worker. */
    fun fireTestNudge() {
        nudgeOneShot.fireNow()
    }

    fun toggleNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.updateNotificationSettings(enabled = enabled).fold(
                onSuccess = { updated ->
                    userState.value = updated
                    if (updated.notificationEnabled) {
                        nudgeScheduler.schedule(updated.notificationHour)
                    } else {
                        nudgeScheduler.cancel()
                    }
                },
                onFailure = { e ->
                    sheet.update { it.copy(error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    fun setNotificationHour(hour: Int) {
        val safeHour = hour.coerceIn(0, 23)
        viewModelScope.launch {
            authRepository.updateNotificationSettings(hour = safeHour).fold(
                onSuccess = { updated ->
                    userState.value = updated
                    if (updated.notificationEnabled) {
                        nudgeScheduler.schedule(updated.notificationHour)
                    }
                },
                onFailure = { e ->
                    sheet.update { it.copy(error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    fun toggleNotificationType(type: NudgeType, enabled: Boolean) {
        val current = userState.value?.notificationTypes ?: NudgeType.entries.toSet()
        val next = if (enabled) current + type else current - type
        viewModelScope.launch {
            authRepository.updateNotificationSettings(types = next).fold(
                onSuccess = { updated -> userState.value = updated },
                onFailure = { e ->
                    sheet.update { it.copy(error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    private fun formatBudgetForInput(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)

    private data class SheetState(
        val showBudget: Boolean = false,
        val budgetInput: String = "",
        val savingBudget: Boolean = false,
        val showExtra: Boolean = false,
        val extraAmountInput: String = "",
        val extraNoteInput: String = "",
        val savingExtra: Boolean = false,
        val monthExtras: List<BudgetExtra> = emptyList(),
        val showPhone: Boolean = false,
        val phoneInput: String = "",
        val savingPhone: Boolean = false,
        val showCurrency: Boolean = false,
        val currencyInput: String = "PEN",
        val savingCurrency: Boolean = false,
        val showDelete: Boolean = false,
        val deleting: Boolean = false,
        val error: String? = null,
    )
}
