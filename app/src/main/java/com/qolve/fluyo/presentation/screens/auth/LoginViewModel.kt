package com.qolve.fluyo.presentation.screens.auth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String?) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onSignInStarted() {
        _uiState.value = LoginUiState.Loading
    }

    fun onSignInFailed(message: String?) {
        _uiState.update { LoginUiState.Error(message) }
    }

    fun onSignInSucceeded() {
        // RootViewModel owns provisioning. Keeping it in this screen-scoped ViewModel
        // allowed navigation to cancel ensureUserRow() halfway through Google sign-in.
        _uiState.value = LoginUiState.Idle
    }

    fun reset() {
        _uiState.value = LoginUiState.Idle
    }
}
