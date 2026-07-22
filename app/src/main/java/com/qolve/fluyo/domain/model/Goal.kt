package com.qolve.fluyo.domain.model

import java.time.Instant
import java.time.LocalDate

data class Goal(
    val id: String,
    val name: String,
    val targetAmount: MoneyAmount,
    val currentAmount: MoneyAmount,
    val deadline: LocalDate?,
    val status: GoalStatus,
    val createdAt: Instant,
    val completedAt: Instant?,
    /**
     * Number of deposits the user has made into this goal. Not persisted on the row —
     * computed at refresh time in `SupabaseGoalRepository` from `goal_deposits`. Default 0
     * keeps test/preview construction lightweight.
     */
    val depositCount: Int = 0,
) {
    val progress: Float
        get() = currentAmount.ratioOf(targetAmount).coerceIn(0f, 1f)

    val remaining: MoneyAmount
        get() = (targetAmount - currentAmount).let { if (it < MoneyAmount.ZERO) MoneyAmount.ZERO else it }

    val isCompleted: Boolean get() = status == GoalStatus.COMPLETED
}

data class GoalDeposit(
    val id: String,
    val goalId: String,
    val amount: MoneyAmount,
    val createdAt: Instant,
)
