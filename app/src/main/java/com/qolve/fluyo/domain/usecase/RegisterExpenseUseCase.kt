package com.qolve.fluyo.domain.usecase

import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.repository.ExpenseRepository
import java.time.LocalDate
import javax.inject.Inject

class RegisterExpenseUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
) {
    suspend operator fun invoke(
        amount: Double,
        categoryId: String?,
        description: String?,
        expenseDate: LocalDate,
        source: ExpenseSource,
        recipient: String? = null,
        imageUrl: String? = null,
    ): Result<Expense> = expenseRepository.register(
        amount = amount,
        categoryId = categoryId,
        description = description,
        expenseDate = expenseDate,
        source = source,
        recipient = recipient,
        imageUrl = imageUrl,
    )
}
