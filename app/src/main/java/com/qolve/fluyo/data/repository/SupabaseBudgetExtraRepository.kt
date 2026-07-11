package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.BudgetExtraDto
import com.qolve.fluyo.data.dto.BudgetExtraInsertDto
import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless CRUD over `budget_extras`. No cached state on purpose — the effective
 * budget lives in SupabaseExpenseRepository's breakdown (fed by the view), so this
 * repo doesn't need SessionScopedCache registration.
 */
@Singleton
class SupabaseBudgetExtraRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) : BudgetExtraRepository {

    override suspend fun addExtra(
        amount: Double,
        note: String?,
        month: YearMonth,
    ): Result<BudgetExtra> = runCatching {
        require(amount > 0.0) { "Extra must be positive" }
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        client.postgrest.from("budget_extras")
            .insert(
                BudgetExtraInsertDto(
                    userId = userId,
                    month = month.atDay(1).toString(),
                    amount = amount,
                    note = note?.takeIf { it.isNotBlank() },
                ),
            ) { select() }
            .decodeSingle<BudgetExtraDto>()
            .toDomain()
    }

    override suspend fun extrasForMonth(month: YearMonth): Result<List<BudgetExtra>> = runCatching {
        val userId = authRepository.currentUserId() ?: return@runCatching emptyList()
        client.postgrest.from("budget_extras")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("month", month.atDay(1).toString())
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<BudgetExtraDto>()
            .map { it.toDomain() }
    }

    override suspend fun deleteExtra(id: String): Result<Unit> = runCatching {
        client.postgrest.from("budget_extras")
            .delete { filter { eq("id", id) } }
    }

    private fun BudgetExtraDto.toDomain() = BudgetExtra(
        id = id,
        amount = amount,
        note = note,
        month = YearMonth.from(LocalDate.parse(month)),
        // Supabase returns offset timestamps ("+00:00"), which Instant.parse rejects.
        createdAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }
            .recoverCatching { Instant.parse(createdAt) }
            .getOrDefault(Instant.EPOCH),
    )
}
