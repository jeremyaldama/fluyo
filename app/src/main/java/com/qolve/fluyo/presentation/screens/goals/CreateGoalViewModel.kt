package com.qolve.fluyo.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.usecase.CreateGoalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val parsedTarget: Double?
        get() = targetInput.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }

    val canSave: Boolean
        get() = !isSaving && name.trim().isNotEmpty() && parsedTarget != null
}

@HiltViewModel
class CreateGoalViewModel @Inject constructor(
    private val createGoal: CreateGoalUseCase,
) : ViewModel() {

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
        _state.update { it.copy(deadline = date, showDatePicker = false) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun save() {
        val current = _state.value
        val target = current.parsedTarget ?: return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = createGoal(current.name.trim(), target, current.deadline)
            result.fold(
                onSuccess = { _state.update { it.copy(isSaving = false, savedOk = true) } },
                onFailure = { e ->
                    _state.update { it.copy(isSaving = false, errorMessage = e.localizedMessage ?: "Error") }
                },
            )
        }
    }
}
