package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import com.qolve.fluyo.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Unit tests for [CreateGoalUseCase]. Uses a hand-written fake repository (the project's
 * MockK is available too, but a fake keeps the delegation test readable).
 */
class CreateGoalUseCaseTest {

    private class FakeGoalRepository : GoalRepository {
        var lastCreateArgs: Triple<String, Double, LocalDate?>? = null
        override fun observeActiveGoals(): Flow<List<Goal>> = emptyFlow()
        override fun observeCompletedGoals(): Flow<List<Goal>> = emptyFlow()
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        override suspend fun createGoal(name: String, target: Double, deadline: LocalDate?): Result<Goal> {
            lastCreateArgs = Triple(name, target, deadline)
            return Result.success(
                Goal(
                    id = "new",
                    name = name,
                    targetAmount = target,
                    currentAmount = 0.0,
                    deadline = deadline,
                    status = GoalStatus.ACTIVE,
                    createdAt = Instant.EPOCH,
                    completedAt = null,
                )
            )
        }
        override suspend fun deposit(goalId: String, amount: Double): Result<GoalDepositOutcome> =
            error("not used")
        override suspend fun deleteGoal(goalId: String): Result<Unit> = error("not used")
    }

    @Test
    fun `invoke delegates to the repository and returns the created goal`() = runTest {
        val repo = FakeGoalRepository()
        val useCase = CreateGoalUseCase(repo)

        val result = useCase("Audífonos", 200.0, LocalDate.of(2026, 12, 31))

        assertTrue(result.isSuccess)
        assertEquals("Audífonos", result.getOrNull()?.name)
        assertEquals(Triple("Audífonos", 200.0, LocalDate.of(2026, 12, 31)), repo.lastCreateArgs)
    }

    @Test
    fun `active goal cap is five`() {
        assertEquals(5, CreateGoalUseCase.MAX_ACTIVE_GOALS)
    }
}
