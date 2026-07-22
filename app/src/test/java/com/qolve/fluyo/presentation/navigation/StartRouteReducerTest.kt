package com.qolve.fluyo.presentation.navigation

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
        assertNull(StartRouteReducer.reduce(previous = null, session = RootSessionState.Unknown, onboardingDone = false))
    }

    @Test
    fun `unknown keeps the previous route (background-foreground session cycle)`() {
        assertEquals(
            Routes.MAIN,
            StartRouteReducer.reduce(previous = Routes.MAIN, session = RootSessionState.Unknown, onboardingDone = true),
        )
        assertEquals(
            Routes.LOGIN,
            StartRouteReducer.reduce(previous = Routes.LOGIN, session = RootSessionState.Unknown, onboardingDone = false),
        )
    }

    @Test
    fun `signed out always routes to login`() {
        assertEquals(
            Routes.LOGIN,
            StartRouteReducer.reduce(previous = Routes.MAIN, session = RootSessionState.SignedOut, onboardingDone = true),
        )
    }

    @Test
    fun `signed in routes to main when onboarded, onboarding otherwise`() {
        val ready = RootSessionState.Ready("auth-1")
        assertEquals(Routes.MAIN, StartRouteReducer.reduce(previous = null, session = ready, onboardingDone = true))
        assertEquals(Routes.ONBOARDING, StartRouteReducer.reduce(previous = null, session = ready, onboardingDone = false))
    }

    @Test
    fun `signed in identity remains on splash until provisioning succeeds`() {
        assertEquals(
            Routes.SPLASH,
            StartRouteReducer.reduce(
                previous = Routes.MAIN,
                session = RootSessionState.Provisioning("auth-2"),
                onboardingDone = true,
            ),
        )
        assertEquals(
            Routes.SPLASH,
            StartRouteReducer.reduce(
                previous = Routes.MAIN,
                session = RootSessionState.Failed("auth-2", "offline"),
                onboardingDone = true,
            ),
        )
    }
}
