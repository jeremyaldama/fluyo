package com.qolve.fluyo.domain.model

data class MonthlyBreakdown(
    /** EFFECTIVE budget for the month: base + one-off extras ("ingreso extra"). */
    val monthlyBudget: MoneyAmount,
    val totalSpent: MoneyAmount,
    /** This month's extras total — display-only; already included in [monthlyBudget]. */
    val extraIncome: MoneyAmount = MoneyAmount.ZERO,
) {
    /** The user's base budget (what next month starts from). */
    val baseBudget: MoneyAmount get() = monthlyBudget - extraIncome
    val remaining: MoneyAmount get() = monthlyBudget - totalSpent
    val percentageUsed: Float
        get() = totalSpent.ratioOf(monthlyBudget).coerceIn(0f, 1f)
    val isOverBudget: Boolean
        get() = monthlyBudget > MoneyAmount.ZERO && totalSpent > monthlyBudget
}
