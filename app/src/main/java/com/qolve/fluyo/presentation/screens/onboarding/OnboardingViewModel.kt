package com.qolve.fluyo.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val budgetInput: String = "",
    val phoneInput: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val parsedBudget: Double?
        get() = budgetInput.replace(',', '.').toDoubleOrNull()

    val canAdvanceFromBudget: Boolean
        get() = parsedBudget != null && parsedBudget!! >= 0.0
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboardingPrefs: OnboardingPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onBudgetChange(value: String) {
        // Allow digits and a single separator
        val filtered = value.filter { it.isDigit() || it == '.' || it == ',' }
        _uiState.update { it.copy(budgetInput = filtered, error = null) }
    }

    /**
     * Convenience for the "Saltar" link in the new onboarding chrome. Marks onboarding as
     * complete without persisting any user input — equivalent to the user finishing the flow
     * with the default budget (0) and no phone number. They can fill these in later from Profile.
     */
    fun skip() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            onboardingPrefs.setCompleted(true)
            _uiState.update { it.copy(isSaving = false, finished = true) }
        }
    }

    fun onPhoneChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(15)
        _uiState.update { it.copy(phoneInput = filtered, error = null) }
    }

    fun next() {
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    }

    fun back() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun finish() {
        val current = _uiState.value
        if (current.isSaving) return
        val budget = current.parsedBudget ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            authRepository.updateProfile(
                monthlyBudget = budget,
                phoneNumber = current.phoneInput.takeIf { it.isNotBlank() },
            )
                .onSuccess {
                    onboardingPrefs.setCompleted(true)
                    _uiState.update { it.copy(isSaving = false, finished = true) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, error = err.message) }
                }
        }
    }

    companion object {
        const val LAST_STEP = 2
    }
}
