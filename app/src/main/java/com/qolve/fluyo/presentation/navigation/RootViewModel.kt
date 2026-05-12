package com.qolve.fluyo.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.repository.AuthRepository
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
import javax.inject.Inject

data class RootUiState(
    val startRoute: String? = null,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboardingPrefs: OnboardingPrefs,
    supabaseClient: SupabaseClient,
) : ViewModel() {

    val composeAuth: ComposeAuth = supabaseClient.composeAuth

    private val _uiState = MutableStateFlow(RootUiState())
    val uiState: StateFlow<RootUiState> = _uiState.asStateFlow()

    init {
        combine(authRepository.authState, onboardingPrefs.completed) { auth, onboardingDone ->
            when (auth) {
                AuthState.Unknown -> null
                AuthState.SignedOut -> Routes.LOGIN
                is AuthState.SignedIn -> if (onboardingDone) Routes.MAIN else Routes.ONBOARDING
            }
        }
            .onEach { route -> _uiState.value = RootUiState(startRoute = route) }
            .launchIn(viewModelScope)
    }

    fun markOnboardingDone() {
        // After onboarding finishes, recompute target route immediately.
        // The Flow above will pick this up because OnboardingViewModel writes to prefs;
        // this method is a no-op safety hook for direct callers.
    }
}
