package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.data.local.NudgePrefs
import com.qolve.fluyo.domain.model.NudgeContent
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.GoalRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Picks at most one nudge to show today based on the user's recent activity,
 * preferences, and a once-per-day guard. Returns null when nothing useful to say.
 *
 * Priority order (first match wins):
 *  1. BUDGET — user is 80%+ through their monthly budget
 *  2. GOAL — any active goal is at 70%+ progress
 *  3. REMINDER — no expense in the last 2 days
 *  4. PROGRESS — current streak of consecutive days with at least one expense
 *
 * Nudge respects:
 *  - user.notificationEnabled
 *  - user.notificationTypes (subset of the 4 types)
 *  - NudgePrefs.lastFiredOn() (max 1 per calendar day)
 */
class ComputeNudgeUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val nudgePrefs: NudgePrefs,
) {

    suspend operator fun invoke(now: LocalDate = LocalDate.now()): NudgeContent? {
        val user = authRepository.currentUser().getOrNull() ?: return null
        if (!user.notificationEnabled) return null

        // Respect "max 1 nudge per day".
        if (nudgePrefs.lastFiredOn() == now) return null

        val enabledTypes = user.notificationTypes

        // Refresh data so worker decisions reflect Supabase truth.
        expenseRepository.refresh()
        goalRepository.refresh()

        val breakdown = expenseRepository.observeMonthlyBreakdown().first()
        if (NudgeType.BUDGET in enabledTypes && breakdown.percentageUsed >= 0.80f) {
            val pct = (breakdown.percentageUsed * 100f).toInt()
            return NudgeContent(
                type = NudgeType.BUDGET,
                title = "Cuidado con tu presupuesto",
                body = "Ya gastaste el ${pct}% de tu presupuesto este mes. ¡Tú puedes! 💪",
            )
        }

        val activeGoals = goalRepository.observeActiveGoals().first()
        val closeGoal = activeGoals
            .filter { it.targetAmount > 0 }
            .maxByOrNull { it.progress }
        if (NudgeType.GOAL in enabledTypes && closeGoal != null && closeGoal.progress >= 0.70f) {
            val pct = (closeGoal.progress * 100f).toInt()
            return NudgeContent(
                type = NudgeType.GOAL,
                title = "¡Cerca de tu meta!",
                body = "Llevas el ${pct}% de \"${closeGoal.name}\". Un empujón más 🎯",
            )
        }

        val expenses = expenseRepository.observeRecentExpenses().first()
        val daysSinceLast = expenses
            .maxOfOrNull { it.expenseDate }
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(it, now) }
        if (NudgeType.REMINDER in enabledTypes && daysSinceLast != null && daysSinceLast >= 2L) {
            return NudgeContent(
                type = NudgeType.REMINDER,
                title = "¿Cómo va tu día?",
                body = "Hace $daysSinceLast días que no registras un gasto 😊",
            )
        }

        if (NudgeType.PROGRESS in enabledTypes) {
            val streak = countStreak(expenses.map { it.expenseDate }.toSet(), now)
            if (streak >= 3) {
                return NudgeContent(
                    type = NudgeType.PROGRESS,
                    title = "¡Buena racha!",
                    body = "Llevas $streak días registrando tus gastos. Sigue así 🎉",
                )
            }
        }

        return null
    }

    private fun countStreak(days: Set<LocalDate>, today: LocalDate): Int {
        var streak = 0
        var cursor = today
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
