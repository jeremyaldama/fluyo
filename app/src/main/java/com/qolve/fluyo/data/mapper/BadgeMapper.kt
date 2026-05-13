package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.BadgeDto
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import java.time.Instant
import java.time.OffsetDateTime

fun BadgeDto.toDomainOrNull(): Badge? {
    val type = BadgeType.fromWire(badgeType) ?: return null
    return Badge(
        id = id,
        type = type,
        unlockedAt = runCatching { OffsetDateTime.parse(unlockedAt).toInstant() }
            .recoverCatching { Instant.parse(unlockedAt) }
            .getOrDefault(Instant.EPOCH),
    )
}
