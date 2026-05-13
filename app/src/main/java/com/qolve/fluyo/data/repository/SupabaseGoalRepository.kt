package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.GoalDepositInsertDto
import com.qolve.fluyo.data.dto.GoalDto
import com.qolve.fluyo.data.dto.GoalInsertDto
import com.qolve.fluyo.data.dto.GoalUpdateDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import com.qolve.fluyo.domain.repository.GoalRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseGoalRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) : GoalRepository {

    private val activeState = MutableStateFlow<List<Goal>>(emptyList())
    private val completedState = MutableStateFlow<List<Goal>>(emptyList())

    override fun observeActiveGoals(): Flow<List<Goal>> = activeState.asStateFlow()
    override fun observeCompletedGoals(): Flow<List<Goal>> = completedState.asStateFlow()

    override suspend fun refresh(): Result<Unit> = runCatching {
        val userId = authRepository.currentUserId() ?: return@runCatching
        val all = client.postgrest.from("goals")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<GoalDto>()
            .map { it.toDomain() }
        activeState.value = all.filter { !it.isCompleted }
        completedState.value = all.filter { it.isCompleted }
    }

    override suspend fun createGoal(
        name: String,
        target: Double,
        deadline: LocalDate?,
    ): Result<Goal> = runCatching {
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        val inserted = client.postgrest.from("goals")
            .insert(
                GoalInsertDto(
                    userId = userId,
                    name = name.trim(),
                    targetAmount = target,
                    deadline = deadline?.toString(),
                ),
            ) { select() }
            .decodeSingle<GoalDto>()
            .toDomain()
        refresh()
        inserted
    }

    override suspend fun deposit(goalId: String, amount: Double): Result<GoalDepositOutcome> = runCatching {
        require(amount > 0.0) { "Deposit must be positive" }
        val userId = authRepository.currentUserId() ?: error("No authenticated user")

        client.postgrest.from("goal_deposits").insert(
            GoalDepositInsertDto(goalId = goalId, userId = userId, amount = amount),
        )

        // Read back the goal, update current_amount and possibly status.
        val before = client.postgrest.from("goals")
            .select { filter { eq("id", goalId) } }
            .decodeSingle<GoalDto>()
            .toDomain()

        val newAmount = before.currentAmount + amount
        val nowCompleted = before.status == GoalStatus.ACTIVE && newAmount >= before.targetAmount

        val update = GoalUpdateDto(
            currentAmount = newAmount,
            status = if (nowCompleted) GoalStatus.COMPLETED.wire else null,
            completedAt = if (nowCompleted) Instant.now().toString() else null,
        )

        val updated = client.postgrest.from("goals")
            .update(update) {
                filter { eq("id", goalId) }
                select()
            }
            .decodeSingle<GoalDto>()
            .toDomain()

        refresh()
        GoalDepositOutcome(goal = updated, justCompleted = nowCompleted)
    }

    override suspend fun deleteGoal(goalId: String): Result<Unit> = runCatching {
        client.postgrest.from("goals").delete { filter { eq("id", goalId) } }
        refresh()
    }
}
