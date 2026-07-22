package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.BadgeType

/** Presentation-independent ports used after a badge is persisted successfully. */
interface BadgeEventPublisher {
    fun publishBadgeUnlocked(type: BadgeType, expectedSessionEpoch: Long)
}

interface BadgeNotificationGateway {
    fun notifyBadgeUnlocked(type: BadgeType, expectedSessionEpoch: Long)
}
