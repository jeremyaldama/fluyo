package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.publishIfCurrent
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.data.dto.GoalDepositRpcParams
import com.qolve.fluyo.data.dto.GoalDepositRpcResultDto
import com.qolve.fluyo.data.dto.GoalCreateRpcParams
import com.qolve.fluyo.data.dto.GoalDto
import com.qolve.fluyo.data.dto.GoalArchiveRpcParams
import com.qolve.fluyo.data.dto.GoalArchiveRpcResultDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.data.mapper.toOutcome
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.suspendRunCatching
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class SupabaseGoalRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val sessionBoundary: SessionBoundary,
) : GoalRepository, SessionScopedCache {

    private val activeState = MutableStateFlow<List<Goal>>(emptyList())
    private val completedState = MutableStateFlow<List<Goal>>(emptyList())

    override fun observeActiveGoals(): Flow<List<Goal>> = activeState.asStateFlow()
    override fun observeCompletedGoals(): Flow<List<Goal>> = completedState.asStateFlow()

    override suspend fun clearForSignOut() {
        activeState.value = emptyList()
        completedState.value = emptyList()
    }

    override suspend fun refresh(): Result<Unit> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: return@suspendRunCatching
        val all = fetchGoals(userId, sessionEpoch)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            activeState.value = all.filter { !it.isCompleted }
            completedState.value = all.filter { it.isCompleted }
        }
    }

    private suspend fun fetchGoals(userId: String, sessionEpoch: Long): List<Goal> =
        collectPostgrestPages { range ->
            val page = client.postgrest.from("goals_with_deposit_count")
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(range)
                }
                .decodeList<GoalDto>()
            sessionBoundary.requireCurrent(sessionEpoch)
            page
        }.map { it.toDomain() }

    override suspend fun createGoal(
        name: String,
        target: MoneyAmount,
        deadline: LocalDate?,
        requestId: String,
    ): Result<Goal> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        require(requestId.isNotBlank()) { "Goal request id must not be blank" }
        val parameters = GoalCreateRpcParams(
            requestId = requestId,
            name = name.trim(),
            targetAmount = target.toTransportDouble(),
            deadline = deadline?.toString(),
        )
        val inserted = client.postgrest.rpc(
            function = "create_goal",
            parameters = Json.encodeToJsonElement(parameters).jsonObject,
        )
            .decodeSingle<GoalDto>()
            .toDomain()
        val all = fetchGoals(userId, sessionEpoch)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            activeState.value = all.filter { !it.isCompleted }
            completedState.value = all.filter { it.isCompleted }
        }
        inserted
    }

    override suspend fun deposit(
        goalId: String,
        amount: MoneyAmount,
        requestId: String,
    ): Result<GoalDepositOutcome> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        require(amount > MoneyAmount.ZERO) { "Deposit must be positive" }
        require(requestId.isNotBlank()) { "Deposit request id must not be blank" }
        val userId = authRepository.currentUserId() ?: error("No authenticated user")

        // The caller owns the idempotency key so a user retry after an uncertain response
        // reaches PostgreSQL with the same request id and cannot apply the deposit twice.
        val parameters = GoalDepositRpcParams(
            goalId = goalId,
            amount = amount.toTransportDouble(),
            requestId = requestId,
        )
        val updated = client.postgrest.rpc(
            function = "deposit_to_goal",
            parameters = Json.encodeToJsonElement(parameters).jsonObject,
        ).decodeSingle<GoalDepositRpcResultDto>()
            .toOutcome()

        val all = fetchGoals(userId, sessionEpoch)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            activeState.value = all.filter { !it.isCompleted }
            completedState.value = all.filter { it.isCompleted }
        }
        updated
    }

    override suspend fun archiveGoal(goalId: String): Result<Unit> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        val outcome = client.postgrest.rpc(
            function = "archive_goal",
            parameters = Json.encodeToJsonElement(
                GoalArchiveRpcParams(goalId = goalId),
            ).jsonObject,
        ).decodeSingle<GoalArchiveRpcResultDto>()
        check(outcome.archived) { "Goal not found or not owned by the authenticated user" }
        val all = fetchGoals(userId, sessionEpoch)
        sessionBoundary.publishIfCurrent(sessionEpoch) {
            activeState.value = all.filter { !it.isCompleted }
            completedState.value = all.filter { it.isCompleted }
        }
    }

    override suspend fun findCreatedByRequestId(requestId: String): Result<Goal?> =
        suspendRunCatching {
            val sessionEpoch = sessionBoundary.snapshot()
            sessionBoundary.requireCurrent(sessionEpoch)
            val userId = authRepository.currentUserId() ?: error("No authenticated user")
            require(requestId.isNotBlank()) { "Goal request id must not be blank" }
            val goal = client.postgrest.from("goals")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("client_request_id", requestId)
                    }
                }
                .decodeSingleOrNull<GoalDto>()
                ?.toDomain()
            if (goal != null) {
                val all = fetchGoals(userId, sessionEpoch)
                sessionBoundary.publishIfCurrent(sessionEpoch) {
                    activeState.value = all.filter { !it.isCompleted }
                    completedState.value = all.filter { it.isCompleted }
                }
            }
            sessionBoundary.requireCurrent(sessionEpoch)
            goal
        }
}
