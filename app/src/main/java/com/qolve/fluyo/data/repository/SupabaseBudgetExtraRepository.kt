package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.BudgetExtraDto
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.data.dto.BudgetExtraCreateRpcParams
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.suspendRunCatching
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Stateless CRUD over `budget_extras`. No cached state on purpose — the effective
 * budget lives in SupabaseExpenseRepository's breakdown (fed by the view), so this
 * repo doesn't need SessionScopedCache registration.
 */
@Singleton
class SupabaseBudgetExtraRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val sessionBoundary: SessionBoundary,
) : BudgetExtraRepository {

    override suspend fun addExtra(
        amount: MoneyAmount,
        note: String?,
        month: YearMonth,
        requestId: String,
    ): Result<BudgetExtra> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        require(amount > MoneyAmount.ZERO) { "Extra must be positive" }
        require(requestId.isNotBlank()) { "Extra request id must not be blank" }
        authRepository.currentUserId() ?: error("No authenticated user")
        val parameters = BudgetExtraCreateRpcParams(
            requestId = requestId,
            month = month.atDay(1).toString(),
            amount = amount.toTransportDouble(),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val extra = client.postgrest.rpc(
            function = "create_budget_extra",
            parameters = Json.encodeToJsonElement(parameters).jsonObject,
        )
            .decodeSingle<BudgetExtraDto>()
            .toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        extra
    }

    override suspend fun extrasForMonth(month: YearMonth): Result<List<BudgetExtra>> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: return@suspendRunCatching emptyList()
        val extras = collectPostgrestPages { range ->
            val page = client.postgrest.from("budget_extras")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("month", month.atDay(1).toString())
                    }
                    order("created_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(range)
                }
                .decodeList<BudgetExtraDto>()
            sessionBoundary.requireCurrent(sessionEpoch)
            page
        }
        extras.map { it.toDomain() }
    }

    override suspend fun deleteExtra(id: String): Result<Unit> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        authRepository.currentUserId() ?: error("No authenticated user")
        client.postgrest.from("budget_extras")
            .delete { filter { eq("id", id) } }
        sessionBoundary.requireCurrent(sessionEpoch)
    }

    override suspend fun findCreatedByRequestId(requestId: String): Result<BudgetExtra?> =
        suspendRunCatching {
            val sessionEpoch = sessionBoundary.snapshot()
            sessionBoundary.requireCurrent(sessionEpoch)
            val userId = authRepository.currentUserId() ?: error("No authenticated user")
            require(requestId.isNotBlank()) { "Extra request id must not be blank" }
            val extra = client.postgrest.from("budget_extras")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("client_request_id", requestId)
                    }
                }
                .decodeSingleOrNull<BudgetExtraDto>()
                ?.toDomain()
            sessionBoundary.requireCurrent(sessionEpoch)
            extra
        }

    private fun BudgetExtraDto.toDomain() = BudgetExtra(
        id = id,
        amount = MoneyAmount.fromTransport(amount, RoundingMode.HALF_EVEN),
        note = note,
        month = YearMonth.from(LocalDate.parse(month)),
        // Supabase returns offset timestamps ("+00:00"), which Instant.parse rejects.
        createdAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }
            .recoverCatching { Instant.parse(createdAt) }
            .getOrDefault(Instant.EPOCH),
    )
}
