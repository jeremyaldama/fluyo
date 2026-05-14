package com.qolve.fluyo.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.model.UserLevel
import com.qolve.fluyo.domain.model.UserLevelCatalog
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.notifications.NudgeOneShot
import com.qolve.fluyo.notifications.NudgeScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** Phone-edit dialog state. Mirrors the budget-dialog pattern for consistency. */
    val showPhoneDialog: Boolean = false,
    val phoneInput: String = "",
    val isSavingPhone: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalPoints: Int get() = badges.sumOf { it.type.points }
    val currentLevel: UserLevel get() = UserLevelCatalog.levelFor(totalPoints)
    val unlockedTypes: Set<BadgeType> get() = badges.map { it.type }.toSet()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository,
    private val expenseRepository: ExpenseRepository,
    private val nudgeScheduler: NudgeScheduler,
    private val nudgeOneShot: NudgeOneShot,
) : ViewModel() {

    private val userState = MutableStateFlow<User?>(null)
    private val streakState = MutableStateFlow(0)
    private val sheet = MutableStateFlow(SheetState())

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
            showPhoneDialog = s.showPhone,
            phoneInput = s.phoneInput,
            isSavingPhone = s.savingPhone,
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
    }

    fun closeBudgetDialog() {
        sheet.update { it.copy(showBudget = false, budgetInput = "", savingBudget = false) }
    }

    fun onBudgetInputChange(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val parts = cleaned.split('.')
        val trimmed = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
            parts.size == 2 -> parts[0] + "." + parts[1].take(2)
            else -> cleaned
        }
        sheet.update { it.copy(budgetInput = trimmed) }
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
                },
                onFailure = { e ->
                    sheet.update {
                        it.copy(savingBudget = false, error = e.localizedMessage ?: "Error")
                    }
                },
            )
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
        val showPhone: Boolean = false,
        val phoneInput: String = "",
        val savingPhone: Boolean = false,
        val error: String? = null,
    )
}
