package com.qolve.fluyo.domain.model

import java.time.Instant

data class Badge(
    val id: String,
    val type: BadgeType,
    val unlockedAt: Instant,
)
