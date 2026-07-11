package com.qolve.fluyo.domain.model

data class MonthlyBreakdown(
    /** EFFECTIVE budget for the month: base + one-off extras ("ingreso extra"). */
    val monthlyBudget: Double,
    val totalSpent: Double,
    /** This month's extras total — display-only; already included in [monthlyBudget]. */
    val extraIncome: Double = 0.0,
) {
    /** The user's base budget (what next month starts from). */
    val baseBudget: Double get() = monthlyBudget - extraIncome
    val remaining: Double get() = monthlyBudget - totalSpent
    val percentageUsed: Float
        get() = if (monthlyBudget <= 0.0) 0f
        else (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f)
    val isOverBudget: Boolean get() = monthlyBudget > 0.0 && totalSpent > monthlyBudget
}
