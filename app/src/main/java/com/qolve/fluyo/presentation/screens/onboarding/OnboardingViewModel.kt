package com.qolve.fluyo.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.BuildConfig
import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.WhatsAppLink
import com.qolve.fluyo.domain.repository.WhatsAppLinkRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.presentation.util.WhatsAppLaunchRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val budgetInput: String = "",
    val whatsAppLink: WhatsAppLink? = null,
    val isLoadingWhatsApp: Boolean = false,
    val isCreatingWhatsAppChallenge: Boolean = false,
    val whatsAppChallengeExpiresAt: Instant? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val parsedBudget: MoneyAmount?
        get() = MoneyAmount.parse(budgetInput, RoundingMode.UNNECESSARY)

    val canAdvanceFromBudget: Boolean
        get() = parsedBudget != null && parsedBudget!! >= MoneyAmount.ZERO
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val whatsAppLinkRepository: WhatsAppLinkRepository,
    private val onboardingPrefs: OnboardingPrefs,
    private val sessionBoundary: SessionBoundary,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val whatsAppLaunches = Channel<WhatsAppLaunchRequest>(Channel.UNLIMITED)
    val whatsAppLaunchEvents: Flow<WhatsAppLaunchRequest> = whatsAppLaunches.receiveAsFlow()

    init {
        if (BuildConfig.WHATSAPP_LINKING_ENABLED) refreshWhatsAppLink()
    }

    fun onBudgetChange(value: String) {
        // Allow digits and a single separator
        val filtered = value.filter { it.isDigit() || it == '.' || it == ',' }
        _uiState.update { it.copy(budgetInput = filtered, error = null) }
    }

    /**
     * Convenience for the "Saltar" link in the new onboarding chrome. Marks onboarding as
     * complete without persisting any user input — equivalent to the user finishing the flow
     * with the default budget (0). WhatsApp linking is independent and always optional.
     */
    fun skip() {
        if (_uiState.value.isSaving) return
        val sessionEpoch = sessionBoundary.snapshot()
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                onboardingPrefs.setCompleted(true, sessionEpoch)
                sessionBoundary.runIfCurrent(sessionEpoch) {
                    _uiState.update { it.copy(isSaving = false, finished = true) }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                sessionBoundary.runIfCurrent(sessionEpoch) {
                    _uiState.update {
                        it.copy(isSaving = false, error = "No se pudo completar la configuración")
                    }
                }
            }
        }
    }

    fun next() {
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(lastStep)) }
    }

    fun back() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun refreshWhatsAppLink() {
        if (!BuildConfig.WHATSAPP_LINKING_ENABLED) return
        if (_uiState.value.isLoadingWhatsApp) return
        val sessionEpoch = sessionBoundary.snapshot()
        _uiState.update { it.copy(isLoadingWhatsApp = true, error = null) }
        viewModelScope.launch {
            whatsAppLinkRepository.currentLink().fold(
                onSuccess = { link ->
                    sessionBoundary.runIfCurrent(sessionEpoch) {
                        _uiState.update {
                            it.copy(
                                whatsAppLink = link,
                                isLoadingWhatsApp = false,
                                whatsAppChallengeExpiresAt = if (link == null) {
                                    it.whatsAppChallengeExpiresAt
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(sessionEpoch) {
                        _uiState.update {
                            it.copy(
                                isLoadingWhatsApp = false,
                                error = "No se pudo consultar WhatsApp",
                            )
                        }
                    }
                },
            )
        }
    }

    fun createWhatsAppChallenge() {
        if (!BuildConfig.WHATSAPP_LINKING_ENABLED) return
        if (_uiState.value.isLoadingWhatsApp || _uiState.value.isCreatingWhatsAppChallenge) return
        val sessionEpoch = sessionBoundary.snapshot()
        _uiState.update { it.copy(isCreatingWhatsAppChallenge = true, error = null) }
        viewModelScope.launch {
            whatsAppLinkRepository.createChallenge().fold(
                onSuccess = { challenge ->
                    sessionBoundary.runIfCurrent(sessionEpoch) {
                        _uiState.update {
                            it.copy(
                                isCreatingWhatsAppChallenge = false,
                                whatsAppChallengeExpiresAt = challenge.expiresAt,
                            )
                        }
                        whatsAppLaunches.trySend(WhatsAppLaunchRequest(challenge.message))
                    }
                },
                onFailure = {
                    sessionBoundary.runIfCurrent(sessionEpoch) {
                        _uiState.update {
                            it.copy(
                                isCreatingWhatsAppChallenge = false,
                                error = "No se pudo iniciar la verificación",
                            )
                        }
                    }
                },
            )
        }
    }

    fun finish() {
        val sessionEpoch = sessionBoundary.snapshot()
        val current = _uiState.value
        if (current.isSaving) return
        val budget = current.parsedBudget ?: return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            authRepository.updateProfile(monthlyBudget = budget)
                .onSuccess {
                    try {
                        onboardingPrefs.setCompleted(true, sessionEpoch)
                        sessionBoundary.runIfCurrent(sessionEpoch) {
                            _uiState.update { it.copy(isSaving = false, finished = true) }
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        sessionBoundary.runIfCurrent(sessionEpoch) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    error = "No se pudo completar la configuración",
                                )
                            }
                        }
                    }
                }
                .onFailure {
                    sessionBoundary.runIfCurrent(sessionEpoch) {
                        _uiState.update {
                            it.copy(isSaving = false, error = "No se pudo completar la configuración")
                        }
                    }
                }
        }
    }

    companion object {
        val lastStep: Int
            get() = if (BuildConfig.WHATSAPP_LINKING_ENABLED) 2 else 1
    }
}
