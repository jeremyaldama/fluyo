package com.qolve.fluyo.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.model.UserLevel
import com.qolve.fluyo.domain.model.UserLevelCatalog
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
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
    val showBudgetDialog: Boolean = false,
    val budgetInput: String = "",
    val isSavingBudget: Boolean = false,
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
) : ViewModel() {

    private val userState = MutableStateFlow<User?>(null)
    private val sheet = MutableStateFlow(SheetState())

    val uiState: StateFlow<ProfileUiState> = combine(
        userState,
        badgeRepository.observeBadges(),
        sheet,
    ) { user, badges, s ->
        ProfileUiState(
            isLoading = user == null,
            user = user,
            badges = badges,
            showBudgetDialog = s.showBudget,
            budgetInput = s.budgetInput,
            isSavingBudget = s.savingBudget,
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

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun formatBudgetForInput(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)

    private data class SheetState(
        val showBudget: Boolean = false,
        val budgetInput: String = "",
        val savingBudget: Boolean = false,
        val error: String? = null,
    )
}
