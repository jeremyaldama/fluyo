package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.CurrentMonthBudgetDto
import com.qolve.fluyo.data.dto.ExpenseDto
import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.domain.model.MoneyAmount
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyMapperTest {

    @Test
    fun `expense transport amount is converted to cents immediately`() {
        val expense = ExpenseDto(
            id = "expense-1",
            userId = "user-1",
            amount = 10.015,
            expenseDate = "2026-07-22",
            source = "manual",
            createdAt = "2026-07-22T12:00:00Z",
        ).toDomain()

        // HALF_EVEN: decimal 10.015 ties to the even cent 10.02.
        assertEquals(MoneyAmount.ofCents(1_002L), expense.amount)
    }

    @Test
    fun `budget view fields become exact amounts before calculations`() {
        val breakdown = CurrentMonthBudgetDto(
            userId = "user-1",
            monthlyBudget = 1000.10,
            totalSpent = 400.20,
            extraIncome = 100.10,
        ).toDomain()

        assertEquals(MoneyAmount.ofCents(100_010L), breakdown.monthlyBudget)
        assertEquals(MoneyAmount.ofCents(40_020L), breakdown.totalSpent)
        assertEquals(MoneyAmount.ofCents(10_010L), breakdown.extraIncome)
        assertEquals(MoneyAmount.ofCents(59_990L), breakdown.remaining)
    }

    @Test
    fun `user budget transport value becomes exact cents`() {
        val user = UserDto(
            id = "user-1",
            authId = "auth-1",
            monthlyBudget = 999.99,
        ).toDomain()

        assertEquals(MoneyAmount.ofCents(99_999L), user.monthlyBudget)
    }
}
