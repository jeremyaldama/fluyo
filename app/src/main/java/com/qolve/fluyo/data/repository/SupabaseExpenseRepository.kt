package com.qolve.fluyo.data.repository

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
) : ExpenseRepository {

    private val expensesState = MutableStateFlow<List<Expense>>(emptyList())
    private val breakdownState = MutableStateFlow(MonthlyBreakdown(0.0, 0.0))

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

    private suspend fun loadBreakdown(userId: String) {
        val row = client.postgrest.from("current_month_budget")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<CurrentMonthBudgetDto>()
        breakdownState.value = row?.toDomain() ?: MonthlyBreakdown(0.0, 0.0)
    }
}
