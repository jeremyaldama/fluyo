package com.qolve.fluyo.domain.model

import java.time.Instant
import java.time.LocalDate

data class Goal(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val deadline: LocalDate?,
    val status: GoalStatus,
    val createdAt: Instant,
    val completedAt: Instant?,
) {
    val progress: Float
        get() = if (targetAmount <= 0.0) 0f
        else (currentAmount / targetAmount).toFloat().coerceIn(0f, 1f)

    val remaining: Double get() = (targetAmount - currentAmount).coerceAtLeast(0.0)

    val isCompleted: Boolean get() = status == GoalStatus.COMPLETED
}

data class GoalDeposit(
    val id: String,
    val goalId: String,
    val amount: Double,
    val createdAt: Instant,
)
