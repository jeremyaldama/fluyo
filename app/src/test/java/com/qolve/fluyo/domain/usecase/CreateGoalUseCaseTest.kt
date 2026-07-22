package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.model.MoneyAmount
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
        var lastCreateArgs: List<Any?>? = null
        var lastDepositArgs: Triple<String, MoneyAmount, String>? = null
        override fun observeActiveGoals(): Flow<List<Goal>> = emptyFlow()
        override fun observeCompletedGoals(): Flow<List<Goal>> = emptyFlow()
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        override suspend fun createGoal(
            name: String,
            target: MoneyAmount,
            deadline: LocalDate?,
            requestId: String,
        ): Result<Goal> {
            lastCreateArgs = listOf(name, target, deadline, requestId)
            return Result.success(
                Goal(
                    id = "new",
                    name = name,
                    targetAmount = target,
                    currentAmount = MoneyAmount.ZERO,
                    deadline = deadline,
                    status = GoalStatus.ACTIVE,
                    createdAt = Instant.EPOCH,
                    completedAt = null,
                )
            )
        }
        override suspend fun deposit(
            goalId: String,
            amount: MoneyAmount,
            requestId: String,
        ): Result<GoalDepositOutcome> {
            lastDepositArgs = Triple(goalId, amount, requestId)
            return Result.success(
                GoalDepositOutcome(
                    goal = Goal(
                        id = goalId,
                        name = "Audífonos",
                        targetAmount = MoneyAmount.ofCents(20_000L),
                        currentAmount = amount,
                        deadline = null,
                        status = GoalStatus.ACTIVE,
                        createdAt = Instant.EPOCH,
                        completedAt = null,
                    ),
                    justCompleted = false,
                ),
            )
        }
        override suspend fun archiveGoal(goalId: String): Result<Unit> = error("not used")
    }

    @Test
    fun `invoke delegates to the repository and returns the created goal`() = runTest {
        val repo = FakeGoalRepository()
        val useCase = CreateGoalUseCase(repo)

        val target = MoneyAmount.ofCents(20_000L)
        val result = useCase(
            "Audífonos",
            target,
            LocalDate.of(2026, 12, 31),
            requestId = "request-create-1",
        )

        assertTrue(result.isSuccess)
        assertEquals("Audífonos", result.getOrNull()?.name)
        assertEquals(
            listOf("Audífonos", target, LocalDate.of(2026, 12, 31), "request-create-1"),
            repo.lastCreateArgs,
        )
    }

    @Test
    fun `active goal cap is five`() {
        assertEquals(5, CreateGoalUseCase.MAX_ACTIVE_GOALS)
    }

    @Test
    fun `deposit use case forwards the stable idempotency key`() = runTest {
        val repo = FakeGoalRepository()
        val useCase = DepositToGoalUseCase(repo)
        val amount = MoneyAmount.ofCents(1_250L)

        val result = useCase("goal-1", amount, "request-1")

        assertTrue(result.isSuccess)
        assertEquals(Triple("goal-1", amount, "request-1"), repo.lastDepositArgs)
    }
}
