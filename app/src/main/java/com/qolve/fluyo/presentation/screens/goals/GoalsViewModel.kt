package com.qolve.fluyo.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.badge.BadgeEngine
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.usecase.DepositToGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val isLoading: Boolean = true,
    val active: List<Goal> = emptyList(),
    val completed: List<Goal> = emptyList(),
    val depositSheetGoal: Goal? = null,
    val depositInput: String = "",
    val isDepositing: Boolean = false,
    val depositError: String? = null,
    val showConfetti: Boolean = false,
    val errorMessage: String? = null,
) {
    val depositAmount: Double?
        get() = depositInput.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }

    val canDeposit: Boolean
        get() = !isDepositing && depositSheetGoal != null && depositAmount != null
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val depositToGoal: DepositToGoalUseCase,
    private val badgeEngine: BadgeEngine,
) : ViewModel() {

    private val sheetState = MutableStateFlow(
        SheetState(open = null, input = "", saving = false, error = null, confetti = false, error2 = null),
    )

    val uiState: StateFlow<GoalsUiState> = combine(
        goalRepository.observeActiveGoals(),
        goalRepository.observeCompletedGoals(),
        sheetState,
    ) { active, completed, sheet ->
        GoalsUiState(
            isLoading = false,
            active = active,
            completed = completed,
            depositSheetGoal = sheet.open,
            depositInput = sheet.input,
            isDepositing = sheet.saving,
            depositError = sheet.error,
            showConfetti = sheet.confetti,
            errorMessage = sheet.error2,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalsUiState(),
    )

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { goalRepository.refresh() }
    }

    fun openDepositSheet(goal: Goal) {
        sheetState.update { it.copy(open = goal, input = "", error = null) }
    }

    fun closeDepositSheet() {
        sheetState.update { it.copy(open = null, input = "", saving = false, error = null) }
    }

    fun onDepositChange(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val parts = cleaned.split('.')
        val trimmed = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
            parts.size == 2 -> parts[0] + "." + parts[1].take(2)
            else -> cleaned
        }
        sheetState.update { it.copy(input = trimmed, error = null) }
    }

    fun deposit() {
        val goal = sheetState.value.open ?: return
        val amount = sheetState.value.input.replace(",", ".").toDoubleOrNull() ?: return
        if (amount <= 0.0) return
        sheetState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = depositToGoal(goal.id, amount)
            result.fold(
                onSuccess = { outcome ->
                    if (outcome.justCompleted) {
                        runCatching { badgeEngine.checkAfterGoalCompleted() }
                    }
                    // "Mil soles ahorrados" accumulates across all goals — check every deposit.
                    runCatching { badgeEngine.checkAfterDeposit() }
                    sheetState.update {
                        it.copy(
                            open = null,
                            input = "",
                            saving = false,
                            confetti = outcome.justCompleted,
                        )
                    }
                },
                onFailure = { e ->
                    sheetState.update { it.copy(saving = false, error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    /** Deletes the goal currently open in the deposit sheet (after UI confirmation). */
    fun deleteGoal() {
        val goal = sheetState.value.open ?: return
        sheetState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            goalRepository.deleteGoal(goal.id).fold(
                onSuccess = {
                    sheetState.update { it.copy(open = null, input = "", saving = false) }
                },
                onFailure = { e ->
                    sheetState.update { it.copy(saving = false, error = e.localizedMessage ?: "Error") }
                },
            )
        }
    }

    fun consumeConfetti() {
        sheetState.update { it.copy(confetti = false) }
    }

    fun consumeError() {
        sheetState.update { it.copy(error2 = null) }
    }

    private data class SheetState(
        val open: Goal?,
        val input: String,
        val saving: Boolean,
        val error: String?,
        val confetti: Boolean,
        val error2: String?,
    )
}
