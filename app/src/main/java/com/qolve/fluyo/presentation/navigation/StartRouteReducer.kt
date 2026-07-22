package com.qolve.fluyo.presentation.navigation

sealed interface RootSessionState {
    data object Unknown : RootSessionState
    data object SignedOut : RootSessionState
    data class Provisioning(val authId: String) : RootSessionState
    data class Ready(val authId: String) : RootSessionState
    data class Failed(val authId: String, val message: String?) : RootSessionState
}

/**
 * Maps a provisioned root session + onboarding state to the root destination.
 *
 * `Unknown` keeps the previous route on purpose: supabase-kt cycles `sessionStatus`
 * through Initializing every time the app goes background→foreground, and letting the
 * route regress to null restarts the NavHost start-destination effect, whose
 * `popUpTo(0)` wipes the back stack — e.g. the OCR confirm screen pushed by a warm
 * share from Yape would be replaced by the dashboard.
 */
object StartRouteReducer {
    fun reduce(previous: String?, session: RootSessionState, onboardingDone: Boolean): String? =
        when (session) {
            RootSessionState.Unknown -> previous
            RootSessionState.SignedOut -> Routes.LOGIN
            is RootSessionState.Provisioning,
            is RootSessionState.Failed,
            -> Routes.SPLASH
            is RootSessionState.Ready -> if (onboardingDone) Routes.MAIN else Routes.ONBOARDING
        }
}
