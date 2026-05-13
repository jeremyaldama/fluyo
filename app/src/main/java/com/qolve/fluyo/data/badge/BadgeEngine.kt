package com.qolve.fluyo.data.badge

import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.presentation.events.AppEvent
import com.qolve.fluyo.presentation.events.AppEvents
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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
    private val appEvents: AppEvents,
) {

    suspend fun checkAfterExpense() {
        authRepository.currentUserId() ?: return
        badgeRepository.refresh()
        val held = badgeRepository.observeBadges().first().map { it.type }.toSet()

        if (BadgeType.FIRST_EXPENSE !in held) {
            tryUnlock(BadgeType.FIRST_EXPENSE)
        }

        // Streak checks require last-30-day expense dates. Pull them once.
        if (BadgeType.STREAK_7 !in held || BadgeType.STREAK_30 !in held) {
            val today = LocalDate.now()
            val expenses = expenseRepository.loadByDateRange(today.minusDays(29), today)
                .getOrDefault(emptyList())
            val distinctDates = expenses.map { it.expenseDate }.toSet()

            if (BadgeType.STREAK_7 !in held) {
                val last7 = (0..6).map { today.minusDays(it.toLong()) }.toSet()
                if (distinctDates.containsAll(last7)) tryUnlock(BadgeType.STREAK_7)
            }
            if (BadgeType.STREAK_30 !in held) {
                val last30 = (0..29).map { today.minusDays(it.toLong()) }.toSet()
                if (distinctDates.containsAll(last30)) tryUnlock(BadgeType.STREAK_30)
            }
        }
    }

    suspend fun checkAfterGoalCompleted() {
        authRepository.currentUserId() ?: return
        badgeRepository.refresh()
        val held = badgeRepository.observeBadges().first().map { it.type }.toSet()
        if (BadgeType.FIRST_GOAL !in held) tryUnlock(BadgeType.FIRST_GOAL)
    }

    private suspend fun tryUnlock(type: BadgeType) {
        val unlocked = badgeRepository.unlockIfMissing(type).getOrDefault(false)
        if (unlocked) appEvents.emit(AppEvent.BadgeUnlocked(type.wire))
    }
}
