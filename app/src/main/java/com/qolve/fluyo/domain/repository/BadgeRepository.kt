package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import kotlinx.coroutines.flow.Flow

interface BadgeRepository {
    fun observeBadges(): Flow<List<Badge>>
    suspend fun refresh(): Result<Unit>
    /** Unlocks a badge if not already held by the user. Returns true when newly inserted. */
    suspend fun unlockIfMissing(type: BadgeType): Result<Boolean>
}
