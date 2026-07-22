package com.qolve.fluyo.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.SignUpOutcome
import com.qolve.fluyo.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SignIn, SignUp }

enum class ConfirmationResendFeedback { Sent, Failed }

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
    val confirmationEmail: String? = null,
    val isResendingConfirmation: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val resendFeedback: ConfirmationResendFeedback? = null,
) {
    val canSubmit: Boolean
        get() = confirmationEmail == null &&
            !isSubmitting && email.isNotBlank() && password.isNotBlank() &&
            (mode == AuthMode.SignIn || name.isNotBlank())

    val canResendConfirmation: Boolean
        get() = confirmationEmail != null &&
            !isResendingConfirmation &&
            resendCooldownSeconds == 0
}

@HiltViewModel
class EmailAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailAuthUiState())
    val uiState: StateFlow<EmailAuthUiState> = _uiState.asStateFlow()
    private var resendRequestJob: Job? = null
    private var resendCooldownJob: Job? = null

    fun setMode(mode: AuthMode) {
        if (_uiState.value.mode == mode) return
        cancelConfirmationWork()
        _uiState.update {
            it.copy(
                mode = mode,
                password = "",
                error = null,
                confirmationEmail = null,
                isResendingConfirmation = false,
                resendCooldownSeconds = 0,
                resendFeedback = null,
            )
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, error = null) }
    fun onEmailChange(value: String) {
        cancelConfirmationWork()
        _uiState.update {
            it.copy(
                email = value.trim(),
                password = if (it.confirmationEmail != null) "" else it.password,
                error = null,
                confirmationEmail = null,
                isResendingConfirmation = false,
                resendCooldownSeconds = 0,
                resendFeedback = null,
            )
        }
    }

    fun onPasswordChange(value: String) = _uiState.update {
        if (it.confirmationEmail != null) it else it.copy(password = value, error = null)
    }

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
            when (s.mode) {
                AuthMode.SignIn -> authRepository.signInWithEmail(s.email, s.password).fold(
                    onSuccess = {
                        // Navigation/provisioning is rooted above this destination and cannot
                        // be cancelled by popping the email screen.
                        _uiState.update { it.copy(isSubmitting = false, signedIn = true) }
                    },
                    onFailure = ::showServerError,
                )

                AuthMode.SignUp -> authRepository
                    .signUpWithEmail(s.email, s.password, s.name)
                    .fold(
                        onSuccess = { outcome ->
                            when (outcome) {
                                SignUpOutcome.Authenticated -> _uiState.update {
                                    it.copy(isSubmitting = false, signedIn = true)
                                }
                                is SignUpOutcome.ConfirmationRequired -> {
                                    _uiState.update {
                                        it.copy(
                                            isSubmitting = false,
                                            password = "",
                                            confirmationEmail = outcome.email,
                                            resendFeedback = null,
                                        )
                                    }
                                    startResendCooldown()
                                }
                            }
                        },
                        onFailure = ::showServerError,
                    )
            }
        }
    }

    fun resendConfirmation() {
        val current = _uiState.value
        val email = current.confirmationEmail ?: return
        if (!current.canResendConfirmation) return

        _uiState.update {
            it.copy(isResendingConfirmation = true, resendFeedback = null)
        }
        // Count every outbound attempt, not only successful responses, so repeated server
        // or connectivity failures cannot be used to bypass the local send rate limit.
        startResendCooldown()
        resendRequestJob = viewModelScope.launch {
            val result = authRepository.resendSignUpConfirmation(email)
            // Switching mode or changing email invalidates an in-flight response.
            if (_uiState.value.confirmationEmail != email) return@launch
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isResendingConfirmation = false,
                            resendFeedback = ConfirmationResendFeedback.Sent,
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isResendingConfirmation = false,
                            resendFeedback = ConfirmationResendFeedback.Failed,
                        )
                    }
                },
            )
        }
    }

    private fun startResendCooldown() {
        resendCooldownJob?.cancel()
        _uiState.update { state ->
            if (state.confirmationEmail == null) state else {
                state.copy(resendCooldownSeconds = RESEND_COOLDOWN_SECONDS)
            }
        }
        resendCooldownJob = viewModelScope.launch {
            repeat(RESEND_COOLDOWN_SECONDS) {
                delay(1_000)
                _uiState.update { state ->
                    if (state.confirmationEmail == null) state else {
                        state.copy(
                            resendCooldownSeconds = (state.resendCooldownSeconds - 1).coerceAtLeast(0),
                        )
                    }
                }
            }
        }
    }

    private fun cancelConfirmationWork() {
        resendRequestJob?.cancel()
        resendCooldownJob?.cancel()
        resendRequestJob = null
        resendCooldownJob = null
    }

    private fun showServerError(error: Throwable) {
        _uiState.update {
            it.copy(isSubmitting = false, error = AuthFormError.Server(null))
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
