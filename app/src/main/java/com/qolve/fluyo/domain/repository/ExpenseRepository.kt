package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ExpenseRepository {
    fun observeRecentExpenses(limit: Int = 50): Flow<List<Expense>>
    fun observeMonthlyBreakdown(): Flow<MonthlyBreakdown>
    suspend fun refresh(): Result<Unit>

    /** Returns all expenses in `[from, to]` (inclusive). Used for Stats breakdowns. */
    suspend fun loadByDateRange(from: LocalDate, to: LocalDate): Result<List<Expense>>

    suspend fun register(
        amount: Double,
        categoryId: String?,
        description: String?,
        expenseDate: LocalDate,
        source: ExpenseSource,
        recipient: String? = null,
        imageUrl: String? = null,
    ): Result<Expense>

    /**
     * Returns the current "streak" — the count of consecutive days, ending today, on which
     * the user has registered at least one expense. Today missing → streak is 0. Used by
     * the Perfil screen's 🔥 streak chip and the STREAK_7 / STREAK_30 badge engine.
     */
    suspend fun currentStreak(today: LocalDate = LocalDate.now()): Int
}
