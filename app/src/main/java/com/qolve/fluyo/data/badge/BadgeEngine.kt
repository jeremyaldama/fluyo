package com.qolve.fluyo.data.badge

import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.UserLevelCatalog
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.notifications.BadgeNotifier
import com.qolve.fluyo.presentation.events.AppEvent
import com.qolve.fluyo.presentation.events.AppEvents
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralizes badge-unlock rules. Call after the relevant write succeeds.
 *
 * The DB has UNIQUE(user_id, badge_type), so [BadgeRepository.unlockIfMissing] is idempotent.
 */
@Singleton
class BadgeEngine @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val budgetExtraRepository: BudgetExtraRepository,
    private val appEvents: AppEvents,
    private val badgeNotifier: BadgeNotifier,
) {

    suspend fun checkAfterExpense() {
        authRepository.currentUserId() ?: return
        badgeRepository.refresh()
        val held = badgeRepository.observeBadges().first().map { it.type }.toSet()

        if (BadgeType.FIRST_EXPENSE !in held) {
            tryUnlock(BadgeType.FIRST_EXPENSE)
        }

        // Streak-window checks require last-30-day expenses. Pull them once.
        val needsWindow = BadgeType.STREAK_7 !in held ||
            BadgeType.STREAK_30 !in held ||
            BadgeType.NO_YAPE !in held
        if (needsWindow) {
            val today = LocalDate.now()
            val expenses = expenseRepository.loadByDateRange(today.minusDays(29), today)
                .getOrDefault(emptyList())
            val distinctDates = expenses.map { it.expenseDate }.toSet()
            val last7 = (0..6).map { today.minusDays(it.toLong()) }.toSet()

            if (BadgeType.STREAK_7 !in held) {
                if (distinctDates.containsAll(last7)) tryUnlock(BadgeType.STREAK_7)
            }
            if (BadgeType.STREAK_30 !in held) {
                val last30 = (0..29).map { today.minusDays(it.toLong()) }.toSet()
                if (distinctDates.containsAll(last30)) tryUnlock(BadgeType.STREAK_30)
            }
            // "Sin Yape": a full tracked week (expense every day, so the absence is
            // meaningful, not just inactivity) with no Yape-scanned payment in it.
            if (BadgeType.NO_YAPE !in held && distinctDates.containsAll(last7)) {
                val yapeInWeek = expenses.any {
                    it.expenseDate in last7 && it.source == ExpenseSource.OCR
                }
                if (!yapeInWeek) tryUnlock(BadgeType.NO_YAPE)
            }
        }
    }

    suspend fun checkAfterGoalCompleted() {
        authRepository.currentUserId() ?: return
        badgeRepository.refresh()
        val held = badgeRepository.observeBadges().first().map { it.type }.toSet()
        if (BadgeType.FIRST_GOAL !in held) tryUnlock(BadgeType.FIRST_GOAL)
    }

    /**
     * "Mil soles ahorrados": lifetime savings across all goals (active + completed)
     * reach S/ 1,000. Called after every successful deposit.
     */
    suspend fun checkAfterDeposit() {
        authRepository.currentUserId() ?: return
        badgeRepository.refresh()
        val held = badgeRepository.observeBadges().first().map { it.type }.toSet()
        if (BadgeType.MIL_SOLES in held) return

        val saved = goalRepository.observeActiveGoals().first().sumOf { it.currentAmount } +
            goalRepository.observeCompletedGoals().first().sumOf { it.currentAmount }
        if (saved >= MIL_SOLES_TARGET) tryUnlock(BadgeType.MIL_SOLES)
    }

    /**
     * Month-end badges (HU-08), evaluated on the **last calendar day of the month**
     * (the daily nudge worker calls this). `UNIQUE(user_id, badge_type)` makes each a
     * one-time achievement.
     *   • SAVER_MONTH — the month closed under budget.
     *   • PERFECT_MONTH — every single day of the month has a registered expense AND
     *     the month closed under budget.
     */
    suspend fun checkMonthEndBadges(today: LocalDate = LocalDate.now()) {
        authRepository.currentUserId() ?: return
        if (today != today.with(TemporalAdjusters.lastDayOfMonth())) return

        // Effective budget = base + this month's extras, queried by DEVICE month.
        // Deliberately NOT the current_month_budget view: this runs ~20:00 Lima
        // (01:00 UTC next day), when the server-side view has already rolled over
        // to the new month and would drop the extras being judged.
        val base = authRepository.currentUser().getOrNull()?.monthlyBudget ?: 0.0
        val extras = budgetExtraRepository
            .extrasForMonth(java.time.YearMonth.from(today))
            .getOrDefault(emptyList())
            .sumOf { it.amount }
        val budget = base + extras
        if (budget <= 0.0) return

        badgeRepository.refresh()
        val held = badgeRepository.observeBadges().first().map { it.type }.toSet()
        if (BadgeType.SAVER_MONTH in held && BadgeType.PERFECT_MONTH in held) return

        val firstOfMonth = today.with(TemporalAdjusters.firstDayOfMonth())
        val expenses = expenseRepository.loadByDateRange(firstOfMonth, today)
            .getOrDefault(emptyList())
        val spent = expenses.sumOf { it.amount }
        val underBudget = spent <= budget

        if (BadgeType.SAVER_MONTH !in held && underBudget) tryUnlock(BadgeType.SAVER_MONTH)

        if (BadgeType.PERFECT_MONTH !in held && underBudget) {
            val trackedDays = expenses.map { it.expenseDate }.toSet()
            val everyDay = (1..today.dayOfMonth).all { day ->
                firstOfMonth.withDayOfMonth(day) in trackedDays
            }
            if (everyDay) tryUnlock(BadgeType.PERFECT_MONTH)
        }
    }

    private suspend fun tryUnlock(type: BadgeType) {
        val unlocked = badgeRepository.unlockIfMissing(type).getOrDefault(false)
        if (unlocked) {
            appEvents.emit(AppEvent.BadgeUnlocked(type.wire))
            badgeNotifier.notify(type)
            rollUpGamification()
        }
    }

    /**
     * Persists the XP rollup on `public.users` so `total_points`/`level` reflect the
     * badges actually held. Kept as a derived write (badges are the source of truth);
     * failures are non-fatal — the Profile screen computes the same sum client-side.
     */
    private suspend fun rollUpGamification() {
        runCatching {
            val total = badgeRepository.observeBadges().first().sumOf { it.type.points }
            val level = UserLevelCatalog.levelFor(total).number
            authRepository.updateGamification(totalPoints = total, level = level)
        }
    }

    private companion object {
        const val MIL_SOLES_TARGET = 1_000.0
    }
}
