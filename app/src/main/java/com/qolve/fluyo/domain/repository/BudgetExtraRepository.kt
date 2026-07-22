package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.BudgetExtra
import com.qolve.fluyo.domain.model.MoneyAmount
import java.time.YearMonth

/**
 * One-off monthly budget extras. Stateless by design: the effective budget the UI
 * shows comes from the `current_month_budget` view (via ExpenseRepository's
 * breakdown); this repo only covers the CRUD the dialogs and BadgeEngine need.
 * `month` is always keyed by the DEVICE date so attribution matches what the user
 * sees (the server-side view uses UTC and may roll a few hours early in Lima).
 */
interface BudgetExtraRepository {
    suspend fun addExtra(
        amount: MoneyAmount,
        note: String?,
        month: YearMonth,
        /** Stable across retries of the same logical creation. */
        requestId: String,
    ): Result<BudgetExtra>
    suspend fun extrasForMonth(month: YearMonth): Result<List<BudgetExtra>>
    suspend fun deleteExtra(id: String): Result<Unit>
    suspend fun findCreatedByRequestId(requestId: String): Result<BudgetExtra?> =
        Result.failure(UnsupportedOperationException("Budget-extra reconciliation unavailable"))
}
