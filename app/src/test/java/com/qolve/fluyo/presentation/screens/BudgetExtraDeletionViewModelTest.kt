package com.qolve.fluyo.presentation.screens

import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.repository.WhatsAppLinkRepository
import com.qolve.fluyo.notifications.NudgeOneShot
import com.qolve.fluyo.notifications.NudgeScheduler
import com.qolve.fluyo.presentation.screens.home.HomeViewModel
import com.qolve.fluyo.presentation.screens.profile.ProfileViewModel
import com.qolve.fluyo.presentation.util.CsvExporter
import com.qolve.fluyo.presentation.util.CurrencyState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetExtraDeletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `home coalesces delete, exposes failure, and refreshes only after success`() =
        runTest(dispatcher) {
            val firstDelete = CompletableDeferred<Result<Unit>>()
            var deleteCalls = 0
            val extras = mockk<BudgetExtraRepository>()
            coEvery { extras.deleteExtra(EXTRA_ID) } coAnswers {
                deleteCalls += 1
                if (deleteCalls == 1) firstDelete.await() else Result.success(Unit)
            }
            coEvery { extras.extrasForMonth(any()) } returns Result.success(emptyList())

            val categories = mockk<CategoryRepository>()
            every { categories.observeCategories() } returns MutableStateFlow(emptyList<Category>())
            coEvery { categories.refresh() } returns Result.success(Unit)
            val expenses = expenseRepository()
            val auth = mockk<AuthRepository>()
            coEvery { auth.currentUser() } returns Result.success(null)
            val boundary = MutableSessionBoundary()
            val viewModel = HomeViewModel(
                expenseRepository = expenses,
                categoryRepository = categories,
                authRepository = auth,
                budgetExtraRepository = extras,
                sessionBoundary = boundary,
                savedStateHandle = SavedStateHandle(),
            )
            advanceUntilIdle()
            val extra = extra()
            viewModel.budgetDialog.value = viewModel.budgetDialog.value.copy(
                showExtra = true,
                monthExtras = listOf(extra),
            )

            viewModel.deleteExtra(extra)
            viewModel.deleteExtra(extra)
            runCurrent()

            assertEquals(setOf(EXTRA_ID), viewModel.budgetDialog.value.deletingExtraIds)
            coVerify(exactly = 1) { extras.deleteExtra(EXTRA_ID) }

            firstDelete.complete(Result.failure(IllegalStateException("offline")))
            advanceUntilIdle()

            assertEquals("No se pudo eliminar el ingreso", viewModel.budgetDialog.value.error)
            assertTrue(viewModel.budgetDialog.value.deletingExtraIds.isEmpty())
            assertEquals(listOf(extra), viewModel.budgetDialog.value.monthExtras)
            coVerify(exactly = 0) { extras.extrasForMonth(any()) }
            // The only refresh so far is Home's initial load.
            coVerify(exactly = 1) { expenses.refresh() }

            viewModel.deleteExtra(extra)
            advanceUntilIdle()

            assertNull(viewModel.budgetDialog.value.error)
            assertTrue(viewModel.budgetDialog.value.deletingExtraIds.isEmpty())
            assertTrue(viewModel.budgetDialog.value.monthExtras.isEmpty())
            coVerify(exactly = 2) { extras.deleteExtra(EXTRA_ID) }
            coVerify(exactly = 1) { extras.extrasForMonth(any()) }
            coVerify(exactly = 2) { expenses.refresh() }
        }

    @Test
    fun `profile exposes delete failure and rejects a later response from a stale epoch`() =
        runTest(dispatcher) {
            val firstDelete = CompletableDeferred<Result<Unit>>()
            val secondDelete = CompletableDeferred<Result<Unit>>()
            var deleteCalls = 0
            val extras = mockk<BudgetExtraRepository>()
            coEvery { extras.deleteExtra(EXTRA_ID) } coAnswers {
                deleteCalls += 1
                if (deleteCalls == 1) firstDelete.await() else secondDelete.await()
            }
            coEvery { extras.extrasForMonth(any()) } returns Result.success(emptyList())
            val expenses = expenseRepository()
            val badges = mockk<BadgeRepository>()
            every { badges.observeBadges() } returns MutableStateFlow(emptyList<Badge>())
            coEvery { badges.refresh() } returns Result.success(Unit)
            val auth = mockk<AuthRepository>()
            coEvery { auth.currentUser() } returns Result.success(null)
            coEvery { auth.hasMonetaryActivity() } returns Result.success(false)
            val whatsApp = mockk<WhatsAppLinkRepository>()
            coEvery { whatsApp.currentLink() } returns Result.success(null)
            val boundary = MutableSessionBoundary()
            val viewModel = ProfileViewModel(
                authRepository = auth,
                badgeRepository = badges,
                expenseRepository = expenses,
                categoryRepository = mockk(relaxed = true),
                budgetExtraRepository = extras,
                whatsAppLinkRepository = whatsApp,
                nudgeScheduler = mockk<NudgeScheduler>(relaxed = true),
                nudgeOneShot = mockk<NudgeOneShot>(relaxed = true),
                currencyState = CurrencyState(),
                csvExporter = mockk<CsvExporter>(relaxed = true),
                sessionBoundary = boundary,
                savedStateHandle = SavedStateHandle(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            advanceUntilIdle()
            val extra = extra()

            viewModel.deleteExtra(extra)
            viewModel.deleteExtra(extra)
            runCurrent()

            assertEquals(setOf(EXTRA_ID), viewModel.uiState.value.deletingExtraIds)
            coVerify(exactly = 1) { extras.deleteExtra(EXTRA_ID) }

            firstDelete.complete(Result.failure(IllegalStateException("offline")))
            advanceUntilIdle()

            assertEquals("No se pudo eliminar el ingreso", viewModel.uiState.value.errorMessage)
            assertTrue(viewModel.uiState.value.deletingExtraIds.isEmpty())
            coVerify(exactly = 0) { extras.extrasForMonth(any()) }
            coVerify(exactly = 0) { expenses.refresh() }

            viewModel.deleteExtra(extra)
            runCurrent()
            coVerify(exactly = 2) { extras.deleteExtra(EXTRA_ID) }

            boundary.current = false
            secondDelete.complete(Result.success(Unit))
            advanceUntilIdle()

            coVerify(exactly = 0) { extras.extrasForMonth(any()) }
            coVerify(exactly = 0) { expenses.refresh() }
            assertFalse(boundary.isCurrent(EPOCH))
        }

    private fun expenseRepository(): ExpenseRepository = mockk<ExpenseRepository>().also { expenses ->
        every { expenses.observeRecentExpenses(any()) } returns MutableStateFlow(emptyList())
        every { expenses.observeMonthlyBreakdown() } returns MutableStateFlow(
            MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO),
        )
        coEvery { expenses.refresh() } returns Result.success(Unit)
        coEvery { expenses.currentStreak() } returns Result.success(0)
    }

    private fun extra() = BudgetExtra(
        id = EXTRA_ID,
        amount = MoneyAmount.ofCents(10_000L),
        note = "Bono",
        month = YearMonth.of(2026, 7),
        createdAt = Instant.EPOCH,
    )

    private class MutableSessionBoundary(
        var current: Boolean = true,
    ) : SessionBoundary {
        override fun snapshot(): Long = if (current) EPOCH else Long.MIN_VALUE

        override fun isCurrent(expectedEpoch: Long): Boolean = current && expectedEpoch == EPOCH

        override fun runIfCurrent(expectedEpoch: Long, action: () -> Unit): Boolean {
            if (!isCurrent(expectedEpoch)) return false
            action()
            return true
        }
    }

    private companion object {
        const val EXTRA_ID = "extra-1"
        const val EPOCH = 7L
    }
}
