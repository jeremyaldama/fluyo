package com.qolve.fluyo.presentation.screens.goals

import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.usecase.CreateGoalUseCase
import com.qolve.fluyo.presentation.util.StableMutationRequestStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import com.qolve.fluyo.domain.time.FluyoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGoalViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `retry after uncertain response reuses key across recreation`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val createGoal = mockk<CreateGoalUseCase>()
        val requestIds = mutableListOf<String>()
        coEvery { createGoal.invoke(any(), any(), any(), capture(requestIds)) } returnsMany listOf(
            Result.failure(IllegalStateException("response lost")),
            Result.success(savedGoal()),
        )

        val first = viewModel(savedState, createGoal)
        fillValidForm(first)
        first.save()
        advanceUntilIdle()
        assertFalse(first.state.value.savedOk)

        val restored = viewModel(savedState, createGoal)
        fillValidForm(restored)
        restored.save()
        advanceUntilIdle()

        assertTrue(restored.state.value.savedOk)
        assertEquals(2, requestIds.size)
        assertEquals(requestIds.first(), requestIds.last())
    }

    @Test
    fun `double submit invokes create once`() = runTest(dispatcher) {
        val createGoal = mockk<CreateGoalUseCase>()
        coEvery { createGoal.invoke(any(), any(), any(), any()) } returns Result.success(savedGoal())
        val viewModel = viewModel(SavedStateHandle(), createGoal)
        fillValidForm(viewModel)

        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.savedOk)
        coVerify(exactly = 1) { createGoal.invoke(any(), any(), any(), any()) }
    }

    @Test
    fun `changed restored goal reconciles prior commit before another creation`() =
        runTest(dispatcher) {
            val savedState = SavedStateHandle()
            val createGoal = mockk<CreateGoalUseCase>()
            coEvery { createGoal.invoke(any(), any(), any(), any()) } returns
                Result.failure(IllegalStateException("response lost"))
            val repository = mockk<GoalRepository>()
            every { repository.observeActiveGoals() } returns MutableStateFlow(emptyList())
            every { repository.observeCompletedGoals() } returns MutableStateFlow(emptyList())
            coEvery { repository.findCreatedByRequestId(any()) } returns Result.success(savedGoal())

            val first = CreateGoalViewModel(createGoal, repository, savedState)
            fillValidForm(first)
            first.save()
            advanceUntilIdle()

            val restored = CreateGoalViewModel(createGoal, repository, savedState)
            fillValidForm(restored)
            restored.onTargetChange("1200.00")
            restored.save()
            advanceUntilIdle()

            assertTrue(restored.state.value.savedOk)
            coVerify(exactly = 1) { createGoal.invoke(any(), any(), any(), any()) }
            coVerify(exactly = 1) { repository.findCreatedByRequestId(any()) }
        }

    @Test
    fun `pending retry reaches server even when committed goal now fills cap`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val pendingId = StableMutationRequestStore(savedState, "goal_create") { "pending-id" }
            .getOrCreate("Laptop", "100000", null)
        val createGoal = mockk<CreateGoalUseCase>()
        coEvery { createGoal.invoke(any(), any(), any(), any()) } returns Result.success(savedGoal())
        val active = (1..CreateGoalUseCase.MAX_ACTIVE_GOALS).map { index ->
            savedGoal().copy(id = "goal-$index")
        }
        val viewModel = viewModel(savedState, createGoal, active)
        fillValidForm(viewModel)

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.savedOk)
        coVerify(exactly = 1) { createGoal.invoke("Laptop", any(), null, pendingId) }
    }

    @Test
    fun `past deadline is rejected by the form`() = runTest(dispatcher) {
        val createGoal = mockk<CreateGoalUseCase>(relaxed = true)
        val viewModel = viewModel(SavedStateHandle(), createGoal)
        fillValidForm(viewModel)

        viewModel.onDeadlinePicked(FluyoTime.today().minusDays(1))

        assertEquals(null, viewModel.state.value.deadline)
        assertFalse(viewModel.state.value.savedOk)
        assertTrue(viewModel.state.value.errorMessage?.contains("pasado") == true)
        coVerify(exactly = 0) { createGoal.invoke(any(), any(), any(), any()) }

        assertFalse(
            CreateGoalUiState(
                name = "Laptop",
                targetInput = "1000.00",
                deadline = FluyoTime.today().minusDays(1),
            ).canSave,
        )
    }

    @Test
    fun `today is an allowed goal deadline`() {
        val createGoal = mockk<CreateGoalUseCase>(relaxed = true)
        val viewModel = viewModel(SavedStateHandle(), createGoal)
        fillValidForm(viewModel)

        val today = FluyoTime.today()
        viewModel.onDeadlinePicked(today)

        assertEquals(today, viewModel.state.value.deadline)
        assertTrue(viewModel.state.value.canSave)
    }

    private fun viewModel(
        savedState: SavedStateHandle,
        createGoal: CreateGoalUseCase,
        activeGoals: List<Goal> = emptyList(),
    ): CreateGoalViewModel {
        val repository = mockk<GoalRepository>()
        every { repository.observeActiveGoals() } returns MutableStateFlow(activeGoals)
        every { repository.observeCompletedGoals() } returns MutableStateFlow(emptyList())
        return CreateGoalViewModel(createGoal, repository, savedState)
    }

    private fun fillValidForm(viewModel: CreateGoalViewModel) {
        viewModel.onNameChange("Laptop")
        viewModel.onTargetChange("1000.00")
    }

    private fun savedGoal() = Goal(
        id = "goal-1",
        name = "Laptop",
        targetAmount = MoneyAmount.ofCents(100_000L),
        currentAmount = MoneyAmount.ZERO,
        deadline = null,
        status = GoalStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        completedAt = null,
    )
}
