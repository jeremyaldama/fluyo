package com.qolve.fluyo.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String?) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onSignInStarted() {
        _uiState.value = LoginUiState.Loading
    }

    fun onSignInFailed(message: String?) {
        _uiState.update { LoginUiState.Error(message) }
    }

    fun onSignInSucceeded() {
        viewModelScope.launch {
            authRepository.ensureUserRow()
                .onFailure { _uiState.value = LoginUiState.Error(it.message) }
                .onSuccess { _uiState.value = LoginUiState.Idle }
        }
    }

    fun reset() {
        _uiState.value = LoginUiState.Idle
    }
}
