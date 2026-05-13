package com.qolve.fluyo.domain.model

data class MonthlyBreakdown(
    val monthlyBudget: Double,
    val totalSpent: Double,
) {
    val remaining: Double get() = monthlyBudget - totalSpent
    val percentageUsed: Float
        get() = if (monthlyBudget <= 0.0) 0f
        else (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f)
    val isOverBudget: Boolean get() = monthlyBudget > 0.0 && totalSpent > monthlyBudget
}
