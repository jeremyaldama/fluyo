package com.qolve.fluyo.domain.model

import java.time.Instant
import java.time.LocalDate

data class Expense(
    val id: String,
    val amount: Double,
    val categoryId: String?,
    val description: String?,
    val expenseDate: LocalDate,
    val source: ExpenseSource,
    val recipient: String?,
    val imageUrl: String?,
    val createdAt: Instant,
)
