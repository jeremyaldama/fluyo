package com.qolve.fluyo.domain.model

import java.time.Instant
import java.time.YearMonth

/**
 * A one-off income added to a single month's budget ("ingreso extra del mes").
 * The base `User.monthlyBudget` is untouched — extras expire naturally when the
 * month rolls over, so a windfall never inflates future months.
 */
data class BudgetExtra(
    val id: String,
    val amount: Double,
    val note: String?,
    val month: YearMonth,
    val createdAt: Instant,
)
