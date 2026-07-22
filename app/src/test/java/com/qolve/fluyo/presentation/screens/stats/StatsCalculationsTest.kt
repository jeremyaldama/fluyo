package com.qolve.fluyo.presentation.screens.stats

import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MoneyAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StatsCalculationsTest {

    @Test
    fun `weekday average includes calendar occurrences with no expenses`() {
        val from = LocalDate.of(2026, 7, 6) // Monday
        val to = LocalDate.of(2026, 7, 19) // two complete weeks
        val expenses = listOf(expense(amountCents = 10_000L, date = from))

        val result = buildWeekdayPattern(from, to, expenses)

        assertEquals(7, result.size)
        assertEquals(MoneyAmount.ofCents(5_000L), result.single { it.dayOfWeek == 1 }.average)
        assertEquals(MoneyAmount.ZERO, result.single { it.dayOfWeek == 2 }.average)
    }

    @Test
    fun `weekday average ignores expenses outside requested range`() {
        val from = LocalDate.of(2026, 7, 6)
        val to = from.plusDays(6)
        val expenses = listOf(
            expense(amountCents = 2_500L, date = from),
            expense(amountCents = 50_000L, date = from.minusWeeks(1)),
        )

        val monday = buildWeekdayPattern(from, to, expenses).single { it.dayOfWeek == 1 }

        assertEquals(MoneyAmount.ofCents(2_500L), monday.average)
    }

    @Test
    fun `weekday average rejects inverted date range`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildWeekdayPattern(
                from = LocalDate.of(2026, 7, 7),
                to = LocalDate.of(2026, 7, 6),
                expenses = emptyList(),
            )
        }
    }

    private fun expense(amountCents: Long, date: LocalDate) = Expense(
        id = "expense-$date-$amountCents",
        amount = MoneyAmount.ofCents(amountCents),
        categoryId = null,
        description = null,
        expenseDate = date,
        source = ExpenseSource.MANUAL,
        recipient = null,
        imageUrl = null,
        createdAt = Instant.parse("2026-07-06T12:00:00Z"),
    )
}
