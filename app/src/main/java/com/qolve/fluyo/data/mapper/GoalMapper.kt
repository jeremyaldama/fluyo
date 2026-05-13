package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.GoalDto
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

fun GoalDto.toDomain(): Goal = Goal(
    id = id,
    name = name,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    deadline = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    status = GoalStatus.fromWire(status),
    createdAt = parseInstant(createdAt),
    completedAt = completedAt?.let { parseInstant(it) },
)

private fun parseInstant(value: String): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrDefault(Instant.EPOCH)
