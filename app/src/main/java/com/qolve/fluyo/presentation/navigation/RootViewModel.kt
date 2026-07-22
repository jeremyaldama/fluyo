package com.qolve.fluyo.presentation.navigation

import android.util.Log
import com.qolve.fluyo.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.SessionIdentityCoordinator
import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.notifications.NudgeScheduler
import com.qolve.fluyo.notifications.AchievementScheduler
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.presentation.events.SharedImageEvents
import com.qolve.fluyo.presentation.util.CurrencyState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composeAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootUiState(
    val startRoute: String? = null,
    val session: RootSessionState = RootSessionState.Unknown,
    val sessionError: String? = null,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboardingPrefs: OnboardingPrefs,
    private val sessionIdentity: SessionIdentityCoordinator,
    supabaseClient: SupabaseClient,
    val appEvents: AppEvents,
    val sharedImageEvents: SharedImageEvents,
    private val nudgeScheduler: NudgeScheduler,
    private val achievementScheduler: AchievementScheduler,
    private val currencyState: CurrencyState,
) : ViewModel() {

    val composeAuth: ComposeAuth = supabaseClient.composeAuth

    /** Active currency code, provided to the composition as a symbol (HU-11). */
    val currencyCode: StateFlow<String> = currencyState.code

    private val _uiState = MutableStateFlow(RootUiState())
    val uiState: StateFlow<RootUiState> = _uiState.asStateFlow()
    private val retryProvisioning = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            combine(authRepository.authState, retryProvisioning) { auth, _ -> auth }
                .collectLatest { auth -> handleAuthState(auth) }
        }
    }

    private suspend fun handleAuthState(auth: AuthState) {
        when (auth) {
            // Initializing is transient on foreground. Preserve both the ready identity and
            // route so warm OCR/share flows are not destroyed.
            AuthState.Unknown -> Unit

            AuthState.SignedOut -> {
                try {
                    sessionIdentity.transitionTo(null)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    Log.e(TAG, "Failed to clear local session state")
                }
                currencyState.set(null)
                nudgeScheduler.cancel()
                achievementScheduler.cancel()
                updateRootSession(RootSessionState.SignedOut, onboardingDone = false)
            }

            is AuthState.SignedIn -> handleSignedIn(auth.authId)
        }
    }

    private suspend fun handleSignedIn(authId: String) {
        val alreadyReady = (_uiState.value.session as? RootSessionState.Ready)?.authId == authId &&
            sessionIdentity.currentIdentity() == authId

        if (!alreadyReady) {
            updateRootSession(RootSessionState.Provisioning(authId), onboardingDone = false)
            nudgeScheduler.cancel()
            achievementScheduler.cancel()

            try {
                sessionIdentity.transitionTo(authId)
                val user = authRepository.ensureUserRow().getOrThrow()
                check(sessionIdentity.currentIdentity() == authId) {
                    "Authenticated identity changed during provisioning"
                }

                currencyState.set(user.currency)
                if (user.notificationEnabled) {
                    nudgeScheduler.schedule(user.notificationHour)
                } else {
                    nudgeScheduler.cancel()
                }
                achievementScheduler.schedule()

                val onboardingDone = onboardingPrefs.completed.first()
                updateRootSession(RootSessionState.Ready(authId), onboardingDone)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                nudgeScheduler.cancel()
                achievementScheduler.cancel()
                Log.e(TAG, "User provisioning failed")
                updateRootSession(
                    RootSessionState.Failed(authId, PROVISIONING_ERROR),
                    onboardingDone = false,
                )
                return
            }
        }

        // This collection is cancelled immediately if the Auth identity changes. It is the
        // only path from Ready to user content, so profile provisioning always finishes first.
        onboardingPrefs.completed.collect { onboardingDone ->
            if (sessionIdentity.currentIdentity() == authId) {
                updateRootSession(RootSessionState.Ready(authId), onboardingDone)
            }
        }
    }

    private fun updateRootSession(session: RootSessionState, onboardingDone: Boolean) {
        val previous = _uiState.value.startRoute
        val route = StartRouteReducer.reduce(previous, session, onboardingDone)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Root session=${session::class.simpleName} route=$route")
        }
        _uiState.value = RootUiState(
            startRoute = route,
            session = session,
            sessionError = (session as? RootSessionState.Failed)?.message
                ?: if (session is RootSessionState.Failed) "Provisioning failed" else null,
        )
    }

    fun retryProvisioning() {
        retryProvisioning.value += 1
    }

    fun markOnboardingDone() {
        // After onboarding finishes, recompute target route immediately.
        // The Flow above will pick this up because OnboardingViewModel writes to prefs;
        // this method is a no-op safety hook for direct callers.
    }

    private companion object {
        const val TAG = "RootViewModel"
        const val PROVISIONING_ERROR = "No se pudo preparar la cuenta. Inténtalo de nuevo."
    }
}
