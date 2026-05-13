package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.CurrentMonthBudgetDto
import com.qolve.fluyo.data.dto.ExpenseDto
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

fun ExpenseDto.toDomain(): Expense = Expense(
    id = id,
    amount = amount,
    categoryId = categoryId,
    description = description,
    expenseDate = LocalDate.parse(expenseDate),
    source = ExpenseSource.fromWire(source),
    recipient = recipient,
    imageUrl = imageUrl,
    createdAt = parseTimestamp(createdAt),
)

fun CurrentMonthBudgetDto.toDomain(): MonthlyBreakdown = MonthlyBreakdown(
    monthlyBudget = monthlyBudget,
    totalSpent = totalSpent,
)

// Postgres returns timestamps either as "2026-05-12T13:45:00.123456+00:00" or with "Z".
// OffsetDateTime handles both shapes.
private fun parseTimestamp(value: String): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrDefault(Instant.EPOCH)
