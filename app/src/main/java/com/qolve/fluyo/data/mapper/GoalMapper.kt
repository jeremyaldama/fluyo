package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.GoalDto
import com.qolve.fluyo.data.dto.GoalDepositRpcResultDto
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.GoalDepositOutcome
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

fun GoalDto.toDomain(): Goal = Goal(
    id = id,
    name = name,
    targetAmount = MoneyAmount.fromTransport(targetAmount, RoundingMode.HALF_EVEN),
    currentAmount = MoneyAmount.fromTransport(currentAmount, RoundingMode.HALF_EVEN),
    deadline = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    status = GoalStatus.fromWire(status),
    createdAt = parseInstant(createdAt),
    completedAt = completedAt?.let { parseInstant(it) },
    depositCount = depositCount,
)

fun GoalDepositRpcResultDto.toOutcome(): GoalDepositOutcome = GoalDepositOutcome(
    goal = Goal(
        id = id,
        name = name,
        targetAmount = MoneyAmount.fromTransport(targetAmount, RoundingMode.HALF_EVEN),
        currentAmount = MoneyAmount.fromTransport(currentAmount, RoundingMode.HALF_EVEN),
        deadline = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        status = GoalStatus.fromWire(status),
        createdAt = parseInstant(createdAt),
        completedAt = completedAt?.let { parseInstant(it) },
        depositCount = depositCount,
    ),
    justCompleted = justCompleted,
)

private fun parseInstant(value: String): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrDefault(Instant.EPOCH)
