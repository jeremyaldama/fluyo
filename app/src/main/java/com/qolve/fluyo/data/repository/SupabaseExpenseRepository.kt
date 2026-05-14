package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.dto.CurrentMonthBudgetDto
import com.qolve.fluyo.data.dto.ExpenseDto
import com.qolve.fluyo.data.dto.ExpenseInsertDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseExpenseRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) : ExpenseRepository, SessionScopedCache {

    private val expensesState = MutableStateFlow<List<Expense>>(emptyList())
    private val breakdownState = MutableStateFlow(MonthlyBreakdown(0.0, 0.0))

    override suspend fun clearForSignOut() {
        expensesState.value = emptyList()
        breakdownState.value = MonthlyBreakdown(0.0, 0.0)
    }

    override fun observeRecentExpenses(limit: Int): Flow<List<Expense>> =
        expensesState.asStateFlow()

    override fun observeMonthlyBreakdown(): Flow<MonthlyBreakdown> =
        breakdownState.asStateFlow()

    override suspend fun refresh(): Result<Unit> = runCatching {
        val userId = authRepository.currentUserId() ?: return@runCatching
        loadExpenses(userId)
        loadBreakdown(userId)
    }

    override suspend fun register(
        amount: Double,
        categoryId: String?,
        description: String?,
        expenseDate: LocalDate,
        source: ExpenseSource,
        recipient: String?,
        imageUrl: String?,
    ): Result<Expense> = runCatching {
        val userId = authRepository.currentUserId() ?: error("No authenticated user")

        val insert = ExpenseInsertDto(
            userId = userId,
            amount = amount,
            categoryId = categoryId,
            description = description?.takeIf { it.isNotBlank() },
            expenseDate = expenseDate.toString(),
            source = source.wire,
            recipient = recipient?.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
        )

        val saved = client.postgrest.from("expenses")
            .insert(insert) { select() }
            .decodeSingle<ExpenseDto>()
            .toDomain()

        // Refresh derived state so observers update.
        loadExpenses(userId)
        loadBreakdown(userId)

        saved
    }

    private suspend fun loadExpenses(userId: String) {
        expensesState.value = client.postgrest.from("expenses")
            .select {
                filter { eq("user_id", userId) }
                order("expense_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<ExpenseDto>()
            .map { it.toDomain() }
    }

    override suspend fun loadByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): Result<List<Expense>> = runCatching {
        val userId = authRepository.currentUserId() ?: return@runCatching emptyList()
        client.postgrest.from("expenses")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("expense_date", from.toString())
                    lte("expense_date", to.toString())
                }
                order("expense_date", Order.DESCENDING)
            }
            .decodeList<ExpenseDto>()
            .map { it.toDomain() }
    }

    private suspend fun loadBreakdown(userId: String) {
        val row = client.postgrest.from("current_month_budget")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<CurrentMonthBudgetDto>()
        breakdownState.value = row?.toDomain() ?: MonthlyBreakdown(0.0, 0.0)
    }

    /**
     * Streak = number of consecutive days, ending today, that have at least one expense.
     *
     * We query only the last 60 distinct dates — long enough to satisfy any badge ceiling
     * we currently award (STREAK_30 is the max) and small enough that we don't pull the
     * whole table over the wire. The window is computed in Kotlin from the returned
     * distinct dates; no DB-side `GROUP BY` is needed since the response volume is small.
     */
    override suspend fun currentStreak(today: LocalDate): Int {
        val userId = authRepository.currentUserId() ?: return 0
        return runCatching {
            val rows = client.postgrest.from("expenses")
                .select(io.github.jan.supabase.postgrest.query.Columns.list("expense_date")) {
                    filter {
                        eq("user_id", userId)
                        gte("expense_date", today.minusDays(60).toString())
                    }
                    order("expense_date", Order.DESCENDING)
                }
                .decodeList<ExpenseDateRefDto>()
            val distinct = rows.map { LocalDate.parse(it.expenseDate) }.toSet()
            var streak = 0
            var cursor = today
            while (cursor in distinct) {
                streak++
                cursor = cursor.minusDays(1)
            }
            streak
        }.getOrElse { 0 }
    }
}

/** Minimal projection for [SupabaseExpenseRepository.currentStreak]. */
@kotlinx.serialization.Serializable
private data class ExpenseDateRefDto(
    @kotlinx.serialization.SerialName("expense_date") val expenseDate: String,
)
