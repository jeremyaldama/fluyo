package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeEventPublisher
import com.qolve.fluyo.domain.repository.BadgeNotificationGateway
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects achievement candidates after a successful write.
 *
 * This class deliberately does not evaluate financial or historical criteria from local flows.
 * [BadgeRepository.unlockIfMissing] delegates each candidate to the authenticated PostgreSQL RPC,
 * which evaluates authoritative account data atomically and returns true only for a new unlock.
 */
@Singleton
class BadgeEngine @Inject constructor(
    private val authRepository: AuthRepository,
    private val badgeRepository: BadgeRepository,
    private val badgeEvents: BadgeEventPublisher,
    private val badgeNotifications: BadgeNotificationGateway,
    private val sessionBoundary: SessionBoundary,
) {

    suspend fun checkAfterExpense() = reconcile(
        BadgeType.FIRST_EXPENSE,
        BadgeType.STREAK_7,
        BadgeType.STREAK_30,
        BadgeType.NO_YAPE,
    )

    suspend fun checkAfterGoalCompleted() = reconcile(BadgeType.FIRST_GOAL)

    suspend fun checkAfterDeposit() = reconcile(BadgeType.MIL_SOLES)

    /** Targeted monthly candidates, retained for focused callers and tests. */
    suspend fun checkClosedMonthBadges() = reconcile(
        BadgeType.SAVER_MONTH,
        BadgeType.PERFECT_MONTH,
    )

    /**
     * Daily authoritative catch-up for every criterion. This repairs the case where a
     * financial RPC committed but its response never reached the app, so no one-shot work
     * could be enqueued. Already-held badges and unmet criteria are normal no-ops server-side.
     */
    suspend fun checkAllBadges() = reconcile(*BadgeType.entries.toTypedArray())

    private suspend fun reconcile(vararg candidates: BadgeType) {
        val sessionEpoch = sessionBoundary.snapshot()
        if (!sessionBoundary.isCurrent(sessionEpoch)) return
        authRepository.currentUserId() ?: return

        badgeRepository.refresh().getOrThrow()
        val held = badgeRepository.observeBadges().first().mapTo(mutableSetOf()) { it.type }
        candidates.forEach { type ->
            if (type !in held && tryUnlock(type, sessionEpoch)) held += type
        }
    }

    private suspend fun tryUnlock(type: BadgeType, expectedSessionEpoch: Long): Boolean {
        if (!sessionBoundary.isCurrent(expectedSessionEpoch)) return false
        val unlocked = badgeRepository.unlockIfMissing(type).getOrThrow()
        if (unlocked) {
            badgeEvents.publishBadgeUnlocked(type, expectedSessionEpoch)
            if (authRepository.currentUser().getOrNull()?.notificationEnabled == true) {
                badgeNotifications.notifyBadgeUnlocked(type, expectedSessionEpoch)
            }
        }
        return unlocked
    }
}
