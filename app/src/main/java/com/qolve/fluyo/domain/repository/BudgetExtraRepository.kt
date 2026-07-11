package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.BudgetExtra
import java.time.YearMonth

/**
 * One-off monthly budget extras. Stateless by design: the effective budget the UI
 * shows comes from the `current_month_budget` view (via ExpenseRepository's
 * breakdown); this repo only covers the CRUD the dialogs and BadgeEngine need.
 * `month` is always keyed by the DEVICE date so attribution matches what the user
 * sees (the server-side view uses UTC and may roll a few hours early in Lima).
 */
interface BudgetExtraRepository {
    suspend fun addExtra(amount: Double, note: String?, month: YearMonth): Result<BudgetExtra>
    suspend fun extrasForMonth(month: YearMonth): Result<List<BudgetExtra>>
    suspend fun deleteExtra(id: String): Result<Unit>
}
