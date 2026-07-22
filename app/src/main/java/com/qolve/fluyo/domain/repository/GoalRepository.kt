package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.MoneyAmount
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class GoalDepositOutcome(val goal: Goal, val justCompleted: Boolean)

interface GoalRepository {
    fun observeActiveGoals(): Flow<List<Goal>>
    fun observeCompletedGoals(): Flow<List<Goal>>
    suspend fun refresh(): Result<Unit>

    suspend fun createGoal(
        name: String,
        target: MoneyAmount,
        deadline: LocalDate?,
        /** Stable across retries of the same logical creation. */
        requestId: String,
    ): Result<Goal>
    suspend fun deposit(
        goalId: String,
        amount: MoneyAmount,
        /** Stable for retries of the same logical deposit; database-enforced idempotency key. */
        requestId: String,
    ): Result<GoalDepositOutcome>
    suspend fun archiveGoal(goalId: String): Result<Unit>

    /** Reconciles an uncertain idempotent create before another payload may be submitted. */
    suspend fun findCreatedByRequestId(requestId: String): Result<Goal?> =
        Result.failure(UnsupportedOperationException("Goal request reconciliation unavailable"))
}
