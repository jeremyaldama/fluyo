package com.qolve.fluyo.data

import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.data.local.NudgePrefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdentityCoordinatorTest {
    @Test
    fun `clears caches before each distinct identity and is idempotent for refreshes`() = runTest {
        val cache = mockk<SessionScopedCache>()
        val coldStartInput = mockk<ColdStartRetainedCache>()
        val onboarding = mockk<OnboardingPrefs>()
        val nudges = mockk<NudgePrefs>()
        coEvery { cache.clearForSignOut() } returns Unit
        coEvery { coldStartInput.clearForSignOut() } returns Unit
        coEvery { onboarding.activateUser(any()) } returns Unit
        coEvery { nudges.activateUser(any()) } returns Unit
        val caches = object : dagger.Lazy<Set<SessionScopedCache>> {
            override fun get(): Set<SessionScopedCache> = setOf(cache, coldStartInput)
        }
        val boundary = SessionEpoch()
        val coordinator = SessionIdentityCoordinator(caches, onboarding, nudges, boundary)

        assertTrue(coordinator.transitionTo("auth-a"))
        assertFalse(coordinator.transitionTo("auth-a"))
        assertTrue(coordinator.transitionTo("auth-b"))
        assertTrue(coordinator.transitionTo(null))

        assertEquals(null, coordinator.currentIdentity())
        assertFalse(boundary.isCurrent(boundary.snapshot()))
        coVerify(exactly = 3) { cache.clearForSignOut() }
        coVerify(exactly = 2) { coldStartInput.clearForSignOut() }
        coVerifyOrder {
            onboarding.activateUser("auth-a")
            nudges.activateUser("auth-a")
            onboarding.activateUser("auth-b")
            nudges.activateUser("auth-b")
            onboarding.activateUser(null)
            nudges.activateUser(null)
        }
    }
}
