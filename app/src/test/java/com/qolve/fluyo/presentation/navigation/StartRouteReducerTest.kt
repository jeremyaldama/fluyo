package com.qolve.fluyo.presentation.navigation

import com.qolve.fluyo.domain.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [StartRouteReducer] — the auth+onboarding → start-route mapping.
 *
 * The critical case is `Unknown` keeping the previous route: supabase-kt cycles
 * `sessionStatus` through Initializing on every background→foreground transition,
 * and regressing the route to null restarted the NavHost effect whose popUpTo(0)
 * wiped the back stack (warm-share-from-Yape bug: OCR screen replaced by dashboard).
 */
class StartRouteReducerTest {

    @Test
    fun `unknown with no previous route stays null (cold start splash)`() {
        assertNull(StartRouteReducer.reduce(previous = null, auth = AuthState.Unknown, onboardingDone = false))
    }

    @Test
    fun `unknown keeps the previous route (background-foreground session cycle)`() {
        assertEquals(
            Routes.MAIN,
            StartRouteReducer.reduce(previous = Routes.MAIN, auth = AuthState.Unknown, onboardingDone = true),
        )
        assertEquals(
            Routes.LOGIN,
            StartRouteReducer.reduce(previous = Routes.LOGIN, auth = AuthState.Unknown, onboardingDone = false),
        )
    }

    @Test
    fun `signed out always routes to login`() {
        assertEquals(
            Routes.LOGIN,
            StartRouteReducer.reduce(previous = Routes.MAIN, auth = AuthState.SignedOut, onboardingDone = true),
        )
    }

    @Test
    fun `signed in routes to main when onboarded, onboarding otherwise`() {
        val signedIn = AuthState.SignedIn("auth-1")
        assertEquals(Routes.MAIN, StartRouteReducer.reduce(previous = null, auth = signedIn, onboardingDone = true))
        assertEquals(Routes.ONBOARDING, StartRouteReducer.reduce(previous = null, auth = signedIn, onboardingDone = false))
    }
}
