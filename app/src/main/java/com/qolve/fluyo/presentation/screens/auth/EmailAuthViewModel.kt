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

enum class AuthMode { SignIn, SignUp }

/** Localizable error code instead of a raw message — UI maps to strings.xml. */
sealed interface AuthFormError {
    data object InvalidEmail : AuthFormError
    data object ShortPassword : AuthFormError
    data object MissingName : AuthFormError
    data class Server(val message: String?) : AuthFormError
}

data class EmailAuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthFormError? = null,
    val signedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank() &&
            (mode == AuthMode.SignIn || name.isNotBlank())
}

@HiltViewModel
class EmailAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailAuthUiState())
    val uiState: StateFlow<EmailAuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode, error = null) }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, error = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value.trim(), error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun consumeSignedIn() = _uiState.update { it.copy(signedIn = false) }

    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit) return

        // Local validation first — surface a typed error so the UI can map it to strings.xml.
        val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        if (!emailRegex.matches(s.email)) {
            _uiState.update { it.copy(error = AuthFormError.InvalidEmail) }
            return
        }
        if (s.password.length < 8) {
            _uiState.update { it.copy(error = AuthFormError.ShortPassword) }
            return
        }
        if (s.mode == AuthMode.SignUp && s.name.isBlank()) {
            _uiState.update { it.copy(error = AuthFormError.MissingName) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val authResult = when (s.mode) {
                AuthMode.SignIn -> authRepository.signInWithEmail(s.email, s.password)
                AuthMode.SignUp -> authRepository.signUpWithEmail(s.email, s.password, s.name)
            }
            authResult.fold(
                onSuccess = {
                    // ensureUserRow creates the public.users row + triggers the category seed.
                    // For sign-in this is a no-op if the row already exists.
                    val ensureResult = authRepository.ensureUserRow()
                    ensureResult.fold(
                        onSuccess = {
                            _uiState.update { it.copy(isSubmitting = false, signedIn = true) }
                        },
                        onFailure = { e ->
                            _uiState.update {
                                it.copy(isSubmitting = false, error = AuthFormError.Server(e.localizedMessage))
                            }
                        },
                    )
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = AuthFormError.Server(e.localizedMessage))
                    }
                },
            )
        }
    }
}
