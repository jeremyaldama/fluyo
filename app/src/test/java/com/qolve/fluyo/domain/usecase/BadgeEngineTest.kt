package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeEventPublisher
import com.qolve.fluyo.domain.repository.BadgeNotificationGateway
import com.qolve.fluyo.domain.repository.BadgeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BadgeEngineTest {

    @Test
    fun `expense asks server to evaluate only expense candidates`() = runTest {
        val fixture = fixture()

        fixture.engine.checkAfterExpense()

        fixture.verifyCandidates(
            BadgeType.FIRST_EXPENSE,
            BadgeType.STREAK_7,
            BadgeType.STREAK_30,
            BadgeType.NO_YAPE,
        )
    }

    @Test
    fun `goal completion asks server to evaluate first goal`() = runTest {
        val fixture = fixture()

        fixture.engine.checkAfterGoalCompleted()

        fixture.verifyCandidates(BadgeType.FIRST_GOAL)
    }

    @Test
    fun `deposit asks server to evaluate lifetime savings`() = runTest {
        val fixture = fixture()

        fixture.engine.checkAfterDeposit()

        fixture.verifyCandidates(BadgeType.MIL_SOLES)
    }

    @Test
    fun `periodic catch-up asks server to evaluate closed-month candidates`() = runTest {
        val fixture = fixture()

        fixture.engine.checkClosedMonthBadges()

        fixture.verifyCandidates(BadgeType.SAVER_MONTH, BadgeType.PERFECT_MONTH)
    }

    @Test
    fun `daily catch-up asks server to evaluate every badge after a lost response`() = runTest {
        val fixture = fixture()

        fixture.engine.checkAllBadges()

        fixture.verifyCandidates(*BadgeType.entries.toTypedArray())
    }

    @Test
    fun `new server-confirmed unlock publishes event and enabled notification`() = runTest {
        val fixture = fixture(unlocked = BadgeType.FIRST_GOAL)
        coEvery { fixture.auth.currentUser() } returns Result.success(user(notifications = true))

        fixture.engine.checkAfterGoalCompleted()

        verify(exactly = 1) { fixture.events.publishBadgeUnlocked(BadgeType.FIRST_GOAL, 0L) }
        verify(exactly = 1) {
            fixture.notifications.notifyBadgeUnlocked(BadgeType.FIRST_GOAL, 0L)
        }
    }

    private fun fixture(unlocked: BadgeType? = null): Fixture {
        val auth = mockk<AuthRepository>()
        coEvery { auth.currentUserId() } returns "user-1"
        val badges = mockk<BadgeRepository>()
        coEvery { badges.refresh() } returns Result.success(Unit)
        every { badges.observeBadges() } returns MutableStateFlow(emptyList())
        BadgeType.entries.forEach { type ->
            coEvery { badges.unlockIfMissing(type) } returns Result.success(type == unlocked)
        }
        val events = mockk<BadgeEventPublisher>(relaxed = true)
        val notifications = mockk<BadgeNotificationGateway>(relaxed = true)
        return Fixture(
            engine = BadgeEngine(
                authRepository = auth,
                badgeRepository = badges,
                badgeEvents = events,
                badgeNotifications = notifications,
                sessionBoundary = SessionEpoch(),
            ),
            auth = auth,
            badges = badges,
            events = events,
            notifications = notifications,
        )
    }

    private fun user(notifications: Boolean) = User(
        id = "user-1",
        authId = "auth-1",
        email = null,
        displayName = null,
        monthlyBudget = MoneyAmount.ZERO,
        currency = "PEN",
        level = 1,
        totalPoints = 0,
        notificationEnabled = notifications,
    )

    private data class Fixture(
        val engine: BadgeEngine,
        val auth: AuthRepository,
        val badges: BadgeRepository,
        val events: BadgeEventPublisher,
        val notifications: BadgeNotificationGateway,
    ) {
        fun verifyCandidates(vararg expected: BadgeType) {
            expected.forEach { type ->
                coVerify(exactly = 1) { badges.unlockIfMissing(type) }
            }
            coVerify(exactly = expected.size) { badges.unlockIfMissing(any()) }
        }
    }
}
