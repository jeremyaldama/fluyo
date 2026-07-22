package com.qolve.fluyo.presentation.screens

import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.usecase.DepositToGoalUseCase
import com.qolve.fluyo.notifications.AchievementScheduler
import com.qolve.fluyo.presentation.screens.expense.AllExpensesViewModel
import com.qolve.fluyo.presentation.screens.goals.GoalsViewModel
import com.qolve.fluyo.presentation.screens.home.HomeViewModel
import com.qolve.fluyo.presentation.screens.profile.ManageCategoriesViewModel
import com.qolve.fluyo.presentation.screens.stats.StatsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelRefreshStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `home stays loading until refresh completes and coalesces resume`() = runTest(dispatcher) {
        val categoryGate = CompletableDeferred<Result<Unit>>()
        val categories = mockk<CategoryRepository>()
        every { categories.observeCategories() } returns MutableStateFlow(emptyList<Category>())
        coEvery { categories.refresh() } coAnswers { categoryGate.await() }
        val expenses = mockk<ExpenseRepository>(relaxed = true)
        every { expenses.observeRecentExpenses(any()) } returns MutableStateFlow(emptyList())
        every { expenses.observeMonthlyBreakdown() } returns MutableStateFlow(
            com.qolve.fluyo.domain.model.MonthlyBreakdown(MoneyAmount.ZERO, MoneyAmount.ZERO),
        )
        coEvery { expenses.refresh() } returns Result.success(Unit)
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.currentUser() } returns Result.success(null)
        val viewModel = HomeViewModel(
            expenseRepository = expenses,
            categoryRepository = categories,
            authRepository = auth,
            budgetExtraRepository = mockk(relaxed = true),
            sessionBoundary = alwaysCurrentSessionBoundary(),
            savedStateHandle = SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { categories.refresh() }

        categoryGate.complete(Result.success(Unit))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `goals exposes initial failure and retry without duplicate refresh`() = runTest(dispatcher) {
        val first = CompletableDeferred<Result<Unit>>()
        var calls = 0
        val repository = mockk<GoalRepository>()
        every { repository.observeActiveGoals() } returns MutableStateFlow(emptyList())
        every { repository.observeCompletedGoals() } returns MutableStateFlow(emptyList())
        coEvery { repository.refresh() } coAnswers {
            calls += 1
            if (calls == 1) first.await() else Result.success(Unit)
        }
        val viewModel = GoalsViewModel(
            goalRepository = repository,
            depositToGoal = mockk(relaxed = true),
            achievementScheduler = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.refresh()
        runCurrent()
        coVerify(exactly = 1) { repository.refresh() }
        first.complete(Result.failure(IllegalStateException("offline")))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `persisted goal deposit closes sheet before scheduling reconciliation`() = runTest(dispatcher) {
        val goal = goal()
        val repository = mockk<GoalRepository>()
        every { repository.observeActiveGoals() } returns MutableStateFlow(listOf(goal))
        every { repository.observeCompletedGoals() } returns MutableStateFlow(emptyList())
        coEvery { repository.refresh() } returns Result.success(Unit)
        val deposit = mockk<DepositToGoalUseCase>()
        coEvery { deposit.invoke(any(), any(), any()) } returns Result.success(
            GoalDepositOutcome(goal.copy(currentAmount = MoneyAmount.ofCents(500L)), false),
        )
        val achievements = mockk<AchievementScheduler>()
        lateinit var viewModel: GoalsViewModel
        every { achievements.reconcileDeposit() } answers {
            assertNull(viewModel.uiState.value.depositSheetGoal)
        }
        viewModel = GoalsViewModel(repository, deposit, achievements, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.openDepositSheet(goal)
        viewModel.onDepositChange("5")
        viewModel.deposit()
        runCurrent()

        assertNull(viewModel.uiState.value.depositSheetGoal)
        assertFalse(viewModel.uiState.value.isDepositing)
        advanceUntilIdle()
        verify(exactly = 1) { achievements.reconcileDeposit() }
    }

    @Test
    fun `stats coalesces resume while initial query is active and shows its failure`() = runTest(dispatcher) {
        val first = CompletableDeferred<Result<List<com.qolve.fluyo.domain.model.Expense>>>()
        var calls = 0
        val expenses = mockk<ExpenseRepository>(relaxed = true)
        coEvery { expenses.loadByDateRange(any(), any()) } coAnswers {
            calls += 1
            if (calls == 1) first.await() else Result.success(emptyList())
        }
        val categories = mockk<CategoryRepository>()
        every { categories.observeCategories() } returns MutableStateFlow(emptyList())
        coEvery { categories.refresh() } returns Result.success(Unit)
        val viewModel = StatsViewModel(expenses, categories)
        runCurrent()

        viewModel.refresh()
        runCurrent()
        coVerify(exactly = 1) { expenses.loadByDateRange(any(), any()) }
        first.complete(Result.failure(IllegalStateException("offline")))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `history coalesces initial refresh and reports category failure honestly`() = runTest(dispatcher) {
        val categoryGate = CompletableDeferred<Result<Unit>>()
        val categories = mockk<CategoryRepository>()
        every { categories.observeCategories() } returns MutableStateFlow(emptyList())
        coEvery { categories.refresh() } coAnswers { categoryGate.await() }
        val expenses = mockk<ExpenseRepository>(relaxed = true)
        coEvery { expenses.loadByDateRange(any(), any()) } returns Result.success(emptyList())
        val viewModel = AllExpensesViewModel(expenses, categories)
        runCurrent()

        viewModel.refresh()
        runCurrent()
        coVerify(exactly = 1) { categories.refresh() }
        categoryGate.complete(Result.failure(IllegalStateException("offline")))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `category manager exposes initial refresh failure and coalesces resume`() =
        runTest(dispatcher) {
            val response = CompletableDeferred<Result<Unit>>()
            val categories = mockk<CategoryRepository>()
            every { categories.observeCategories() } returns MutableStateFlow(emptyList())
            coEvery { categories.refresh() } coAnswers { response.await() }
            val viewModel = ManageCategoriesViewModel(categories)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            runCurrent()

            viewModel.refresh()
            runCurrent()
            coVerify(exactly = 1) { categories.refresh() }
            response.complete(Result.failure(IllegalStateException("offline")))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.hasLoaded)
            assertNotNull(viewModel.uiState.value.loadErrorMessage)
        }

    private fun goal() = Goal(
        id = "goal-1",
        name = "Laptop",
        targetAmount = MoneyAmount.ofCents(10_000L),
        currentAmount = MoneyAmount.ZERO,
        deadline = null,
        status = GoalStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        completedAt = null,
    )

    private fun alwaysCurrentSessionBoundary(): SessionBoundary = object : SessionBoundary {
        override fun snapshot(): Long = 1L
        override fun isCurrent(expectedEpoch: Long): Boolean = expectedEpoch == 1L
        override fun runIfCurrent(expectedEpoch: Long, action: () -> Unit): Boolean {
            if (!isCurrent(expectedEpoch)) return false
            action()
            return true
        }
    }
}
