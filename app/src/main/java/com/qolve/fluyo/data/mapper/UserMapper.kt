package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import java.time.Instant

fun UserDto.toDomain(): User = User(
    id = id,
    authId = authId,
    email = email,
    displayName = displayName,
    phoneNumber = phoneNumber,
    monthlyBudget = monthlyBudget,
    currency = currency,
    level = level,
    totalPoints = totalPoints,
    notificationEnabled = notificationEnabled,
    notificationHour = notificationHour.coerceIn(0, 23),
    notificationTypes = notificationTypes
        .mapNotNull { NudgeType.fromWire(it) }
        .toSet()
        .ifEmpty { NudgeType.entries.toSet() },
    memberSince = createdAt?.let {
        // Postgres returns "2025-11-04T12:34:56+00" or similar. Instant.parse handles the
        // ISO-8601 form with offset; runCatching guards against any unexpected variants.
        runCatching { Instant.parse(it) }.getOrNull()
    },
)
