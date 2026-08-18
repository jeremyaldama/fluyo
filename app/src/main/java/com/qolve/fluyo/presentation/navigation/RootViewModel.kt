package com.qolve.fluyo.presentation.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.notifications.NudgeScheduler
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.presentation.events.GmailOAuthEvents
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootUiState(
    val startRoute: String? = null,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboardingPrefs: OnboardingPrefs,
    supabaseClient: SupabaseClient,
    val appEvents: AppEvents,
    val sharedImageEvents: SharedImageEvents,
    val gmailOAuthEvents: GmailOAuthEvents,
    private val nudgeScheduler: NudgeScheduler,
    private val currencyState: CurrencyState,
) : ViewModel() {

    val composeAuth: ComposeAuth = supabaseClient.composeAuth

    /** Active currency code, provided to the composition as a symbol (HU-11). */
    val currencyCode: StateFlow<String> = currencyState.code

    private val _uiState = MutableStateFlow(RootUiState())
    val uiState: StateFlow<RootUiState> = _uiState.asStateFlow()

    init {
        try {
            combine(authRepository.authState, onboardingPrefs.completed) { auth, onboardingDone ->
                // Unknown keeps the last known route (see StartRouteReducer): the session
                // status cycles through Initializing on background→foreground, and a null
                // regression would re-trigger the popUpTo(0) start navigation in the NavHost.
                StartRouteReducer.reduce(_uiState.value.startRoute, auth, onboardingDone)
            }
                .onEach { route ->
                    Log.d("RootViewModel", "Navigation route determined: $route")
                    _uiState.value = RootUiState(startRoute = route)
                }
                .launchIn(viewModelScope)

            authRepository.authState
                .onEach { state -> syncNudgeScheduling(state) }
                .launchIn(viewModelScope)
        } catch (e: Exception) {
            Log.e("RootViewModel", "Error initializing root view model", e)
            _uiState.value = RootUiState(startRoute = Routes.LOGIN)
        }
    }

    private fun syncNudgeScheduling(state: AuthState) {
        when (state) {
            is AuthState.SignedIn -> viewModelScope.launch {
                val user = authRepository.currentUser().getOrNull()
                user?.currency?.let { currencyState.set(it) }
                if (user?.notificationEnabled == true) {
                    nudgeScheduler.schedule(user.notificationHour)
                } else {
                    nudgeScheduler.cancel()
                }
            }
            AuthState.SignedOut -> nudgeScheduler.cancel()
            AuthState.Unknown -> Unit
        }
    }

    fun markOnboardingDone() {
        // After onboarding finishes, recompute target route immediately.
        // The Flow above will pick this up because OnboardingViewModel writes to prefs;
        // this method is a no-op safety hook for direct callers.
    }
}
