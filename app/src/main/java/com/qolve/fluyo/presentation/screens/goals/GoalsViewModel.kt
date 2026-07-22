package com.qolve.fluyo.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.usecase.DepositToGoalUseCase
import com.qolve.fluyo.notifications.AchievementScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

data class GoalsUiState(
    val hasLoaded: Boolean = false,
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
    val depositAmount: MoneyAmount?
        get() = MoneyAmount.parse(depositInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }

    val canDeposit: Boolean
        get() = !isDepositing && depositSheetGoal != null && depositAmount != null
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val depositToGoal: DepositToGoalUseCase,
    private val achievementScheduler: AchievementScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pendingDepositRequests = PendingDepositRequestStore(savedStateHandle)

    private val sheetState = MutableStateFlow(
        SheetState(open = null, input = "", saving = false, error = null, confetti = false, error2 = null),
    )

    private data class LoadState(
        val hasLoaded: Boolean = false,
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
    )

    private val loadState = MutableStateFlow(LoadState())
    private var refreshJob: Job? = null

    val uiState: StateFlow<GoalsUiState> = combine(
        goalRepository.observeActiveGoals(),
        goalRepository.observeCompletedGoals(),
        sheetState,
        loadState,
    ) { active, completed, sheet, load ->
        GoalsUiState(
            hasLoaded = load.hasLoaded,
            isLoading = load.isLoading && !load.hasLoaded,
            active = active,
            completed = completed,
            depositSheetGoal = sheet.open,
            depositInput = sheet.input,
            isDepositing = sheet.saving,
            depositError = sheet.error,
            showConfetti = sheet.confetti,
            errorMessage = load.errorMessage ?: sheet.error2,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoalsUiState(),
    )

    init {
        refresh()
        reconcilePendingDeposit()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hadContent = loadState.value.hasLoaded
            loadState.update { it.copy(isLoading = true, errorMessage = null) }
            goalRepository.refresh().fold(
                onSuccess = {
                    loadState.value = LoadState(hasLoaded = true, isLoading = false)
                },
                onFailure = {
                    loadState.value = LoadState(
                        hasLoaded = hadContent,
                        isLoading = false,
                        errorMessage = "No se pudieron cargar las metas",
                    )
                },
            )
        }
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
        if (sheetState.value.saving) return
        val goal = sheetState.value.open ?: return
        val amount = MoneyAmount.parse(sheetState.value.input, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }
            ?: return
        val pending = pendingDepositRequests.pending()
        if (pending != null && !pending.matches(goal.id, amount)) {
            sheetState.update {
                it.copy(error = "Primero verificaremos el depósito pendiente anterior")
            }
            reconcilePendingDeposit()
            return
        }
        sheetState.update { it.copy(saving = true, error = null) }
        val requestId = pendingDepositRequests.getOrCreate(goal.id, amount)
        viewModelScope.launch {
            val result = depositToGoal(goal.id, amount, requestId)
            result.fold(
                onSuccess = { outcome ->
                    pendingDepositRequests.complete(requestId)
                    // The deposit is already durable. Close the sheet immediately and let
                    // optional badge reconciliation happen outside the success path.
                    sheetState.update {
                        it.copy(
                            open = null,
                            input = "",
                            saving = false,
                            confetti = outcome.justCompleted,
                        )
                    }
                    if (outcome.justCompleted) {
                        runCatching { achievementScheduler.reconcileGoalCompletion() }
                    }
                    // "Mil ahorrados" accumulates across all goals in the fixed base currency.
                    runCatching { achievementScheduler.reconcileDeposit() }
                },
                onFailure = { e ->
                    sheetState.update { it.copy(saving = false, error = "No se pudo guardar el depósito") }
                },
            )
        }
    }

    /** Archives the goal currently open in the deposit sheet (after UI confirmation). */
    fun archiveGoal() {
        val goal = sheetState.value.open ?: return
        if (pendingDepositRequests.pending() != null) {
            sheetState.update {
                it.copy(error = "Hay un depósito pendiente de verificar antes de eliminar la meta")
            }
            reconcilePendingDeposit()
            return
        }
        sheetState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            goalRepository.archiveGoal(goal.id).fold(
                onSuccess = {
                    sheetState.update { it.copy(open = null, input = "", saving = false) }
                },
                onFailure = { e ->
                    sheetState.update { it.copy(saving = false, error = "No se pudo eliminar la meta") }
                },
            )
        }
    }

    fun consumeConfetti() {
        sheetState.update { it.copy(confetti = false) }
    }

    fun consumeError() {
        loadState.update { it.copy(errorMessage = null) }
        sheetState.update { it.copy(error2 = null) }
    }

    /** Replays the exact persisted intent, so process death cannot turn it into a new deposit. */
    private fun reconcilePendingDeposit() {
        val pending = pendingDepositRequests.pending() ?: return
        if (sheetState.value.saving) return
        sheetState.update { it.copy(saving = true) }
        viewModelScope.launch {
            depositToGoal(pending.goalId, pending.amount, pending.requestId).fold(
                onSuccess = { outcome ->
                    pendingDepositRequests.complete(pending.requestId)
                    sheetState.update {
                        it.copy(
                            open = null,
                            input = "",
                            saving = false,
                            error = null,
                            error2 = "Se confirmó el depósito pendiente anterior",
                            confetti = outcome.justCompleted,
                        )
                    }
                    if (outcome.justCompleted) {
                        runCatching { achievementScheduler.reconcileGoalCompletion() }
                    }
                    runCatching { achievementScheduler.reconcileDeposit() }
                },
                onFailure = {
                    sheetState.update {
                        it.copy(
                            saving = false,
                            error = "No se pudo verificar el depósito pendiente",
                        )
                    }
                },
            )
        }
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

/**
 * Keeps one in-flight logical deposit across UI retries and process recreation. A retry with
 * the same goal and exact cents reuses its request id. Only a confirmed success ends it;
 * dismissing UI cannot forget an attempt whose server response was uncertain.
 */
internal class PendingDepositRequestStore(
    private val state: SavedStateHandle,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    fun getOrCreate(goalId: String, amount: MoneyAmount): String {
        pending()?.let { existing ->
            check(existing.matches(goalId, amount)) {
                "A different pending deposit must be reconciled first"
            }
            return existing.requestId
        }

        return newRequestId().also { requestId ->
            state[GOAL_ID] = goalId
            state[AMOUNT_CENTS] = amount.cents
            state[REQUEST_ID] = requestId
        }
    }

    fun complete(requestId: String) {
        if (state.get<String>(REQUEST_ID) == requestId) clear()
    }

    fun pending(): PendingDeposit? {
        val requestId = state.get<String>(REQUEST_ID)?.takeIf(String::isNotBlank) ?: return null
        val goalId = state.get<String>(GOAL_ID)?.takeIf(String::isNotBlank) ?: return null
        val amount = state.get<Long>(AMOUNT_CENTS)?.let(MoneyAmount::ofCents) ?: return null
        return PendingDeposit(goalId, amount, requestId)
    }

    private fun clear() {
        state.remove<String>(GOAL_ID)
        state.remove<Long>(AMOUNT_CENTS)
        state.remove<String>(REQUEST_ID)
    }

    private companion object {
        const val GOAL_ID = "pending_deposit_goal_id"
        const val AMOUNT_CENTS = "pending_deposit_amount_cents"
        const val REQUEST_ID = "pending_deposit_request_id"
    }
}

internal data class PendingDeposit(
    val goalId: String,
    val amount: MoneyAmount,
    val requestId: String,
) {
    fun matches(candidateGoalId: String, candidateAmount: MoneyAmount): Boolean =
        goalId == candidateGoalId && amount == candidateAmount
}
