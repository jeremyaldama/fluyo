package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.repository.NudgeHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeNudgeUseCaseTest {

    @Test
    fun `progress nudge uses authoritative streak instead of truncated recent cache`() = runTest {
        val auth = mockk<AuthRepository>()
        coEvery { auth.currentUser() } returns Result.success(
            User(
                id = "user-1",
                authId = "auth-1",
                email = null,
                displayName = null,
                monthlyBudget = MoneyAmount.ZERO,
                currency = "PEN",
                level = 1,
                totalPoints = 0,
                notificationTypes = setOf(NudgeType.PROGRESS),
            ),
        )
        val expenses = mockk<ExpenseRepository>()
        coEvery { expenses.refresh() } returns Result.success(Unit)
        every { expenses.observeMonthlyBreakdown() } returns MutableStateFlow(
            MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO),
        )
        every { expenses.observeRecentExpenses(any()) } returns MutableStateFlow(emptyList())
        coEvery { expenses.currentStreak() } returns Result.success(7)
        val goals = mockk<GoalRepository>()
        coEvery { goals.refresh() } returns Result.success(Unit)
        every { goals.observeActiveGoals() } returns MutableStateFlow(emptyList())
        val history = mockk<NudgeHistoryRepository>()
        coEvery { history.lastFiredOn(any()) } returns null

        val decision = ComputeNudgeUseCase(
            authRepository = auth,
            expenseRepository = expenses,
            goalRepository = goals,
            nudgeHistory = history,
            sessionBoundary = SessionEpoch(),
        )(LocalDate.of(2026, 7, 22))

        assertEquals(NudgeType.PROGRESS, decision?.content?.type)
        assertEquals("Llevas 7 días registrando tus gastos. Sigue así 🎉", decision?.content?.body)
        coVerify(exactly = 1) { expenses.currentStreak() }
    }
}
