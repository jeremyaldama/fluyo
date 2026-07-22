package com.qolve.fluyo.presentation.screens.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.time.FluyoTime
import com.qolve.fluyo.domain.usecase.CreateGoalUseCase
import com.qolve.fluyo.domain.usecase.CreateGoalUseCase.Companion.MAX_ACTIVE_GOALS
import com.qolve.fluyo.presentation.util.StableMutationRequestStore
import com.qolve.fluyo.presentation.util.PendingMutationResolution
import com.qolve.fluyo.presentation.util.isAllowedGoalDeadline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.math.RoundingMode
import javax.inject.Inject

data class CreateGoalUiState(
    val name: String = "",
    val targetInput: String = "",
    val deadline: LocalDate? = null,
    val showDatePicker: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedOk: Boolean = false,
) {
    val parsedTarget: MoneyAmount?
        get() = MoneyAmount.parse(targetInput, RoundingMode.UNNECESSARY)
            ?.takeIf { it > MoneyAmount.ZERO }

    val canSave: Boolean
        get() = !isSaving &&
            name.trim().isNotEmpty() &&
            parsedTarget != null &&
            isAllowedGoalDeadline(deadline, FluyoTime.today())
}

@HiltViewModel
class CreateGoalViewModel @Inject constructor(
    private val createGoal: CreateGoalUseCase,
    private val goalRepository: GoalRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pendingCreateRequest = StableMutationRequestStore(
        state = savedStateHandle,
        namespace = "goal_create",
    )

    private val _state = MutableStateFlow(CreateGoalUiState())
    val state: StateFlow<CreateGoalUiState> = _state.asStateFlow()

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(60)) }
    }

    fun onTargetChange(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val parts = cleaned.split('.')
        val trimmed = when {
            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
            parts.size == 2 -> parts[0] + "." + parts[1].take(2)
            else -> cleaned
        }
        _state.update { it.copy(targetInput = trimmed) }
    }

    fun openDatePicker() {
        _state.update { it.copy(showDatePicker = true) }
    }

    fun closeDatePicker() {
        _state.update { it.copy(showDatePicker = false) }
    }

    fun onDeadlinePicked(date: LocalDate?) {
        if (!isAllowedGoalDeadline(date, FluyoTime.today())) {
            _state.update {
                it.copy(
                    showDatePicker = false,
                    errorMessage = "La fecha límite no puede estar en el pasado",
                )
            }
            return
        }
        _state.update {
            it.copy(deadline = date, showDatePicker = false, errorMessage = null)
        }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.savedOk) return
        val target = current.parsedTarget ?: return
        val normalizedName = current.name.trim()
        if (
            normalizedName.isEmpty() ||
            !isAllowedGoalDeadline(current.deadline, FluyoTime.today())
        ) return
        val payload = arrayOf(
            normalizedName,
            target.cents.toString(),
            current.deadline?.toString(),
        )
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val wasExactRetry = pendingCreateRequest.existing(*payload) != null
            val resolution = pendingCreateRequest.resolve(
                *payload,
                findCommitted = goalRepository::findCreatedByRequestId,
            ).getOrElse {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "No se pudo verificar el intento anterior",
                    )
                }
                return@launch
            }
            if (resolution is PendingMutationResolution.Committed) {
                _state.update { it.copy(isSaving = false, savedOk = true) }
                return@launch
            }
            val requestId = (resolution as PendingMutationResolution.Ready).requestId
            // HU-07: enforce the active-goals cap. Checked here (not just in the UI) so the
            // limit holds even if the screen is reached with the cap already met. A pending
            // idempotent retry must still reach PostgreSQL: its first response may have been
            // lost after committing, in which case the newly created goal already fills the cap.
            if (
                !wasExactRetry &&
                goalRepository.observeActiveGoals().first().size >= MAX_ACTIVE_GOALS
            ) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Llegaste al máximo de $MAX_ACTIVE_GOALS metas activas. " +
                            "Completa o elimina una para crear otra.",
                    )
                }
                pendingCreateRequest.complete(requestId)
                return@launch
            }
            val result = createGoal(normalizedName, target, current.deadline, requestId)
            result.fold(
                onSuccess = {
                    pendingCreateRequest.complete(requestId)
                    _state.update { it.copy(isSaving = false, savedOk = true) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isSaving = false, errorMessage = "No se pudo crear la meta") }
                },
            )
        }
    }
}
