package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.Goal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class GoalDepositOutcome(val goal: Goal, val justCompleted: Boolean)

interface GoalRepository {
    fun observeActiveGoals(): Flow<List<Goal>>
    fun observeCompletedGoals(): Flow<List<Goal>>
    suspend fun refresh(): Result<Unit>

    suspend fun createGoal(name: String, target: Double, deadline: LocalDate?): Result<Goal>
    suspend fun deposit(goalId: String, amount: Double): Result<GoalDepositOutcome>
    suspend fun deleteGoal(goalId: String): Result<Unit>
}
