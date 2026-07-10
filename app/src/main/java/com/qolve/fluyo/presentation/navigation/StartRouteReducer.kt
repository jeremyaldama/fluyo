package com.qolve.fluyo.presentation.navigation

import com.qolve.fluyo.domain.model.AuthState

/**
 * Maps auth + onboarding state to the root start destination.
 *
 * `Unknown` keeps the previous route on purpose: supabase-kt cycles `sessionStatus`
 * through Initializing every time the app goes background→foreground, and letting the
 * route regress to null restarts the NavHost start-destination effect, whose
 * `popUpTo(0)` wipes the back stack — e.g. the OCR confirm screen pushed by a warm
 * share from Yape would be replaced by the dashboard.
 */
object StartRouteReducer {
    fun reduce(previous: String?, auth: AuthState, onboardingDone: Boolean): String? =
        when (auth) {
            AuthState.Unknown -> previous
            AuthState.SignedOut -> Routes.LOGIN
            is AuthState.SignedIn -> if (onboardingDone) Routes.MAIN else Routes.ONBOARDING
        }
}
