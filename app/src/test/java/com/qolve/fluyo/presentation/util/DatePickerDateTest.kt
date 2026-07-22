package com.qolve.fluyo.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DatePickerDateTest {

    @Test
    fun `local date becomes midnight UTC expected by Material date picker`() {
        val date = LocalDate.of(2026, 7, 22)

        assertEquals(
            Instant.parse("2026-07-22T00:00:00Z").toEpochMilli(),
            date.toDatePickerUtcMillis(),
        )
    }

    @Test
    fun `date picker millis round trip preserves calendar date`() {
        listOf(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
        ).forEach { date ->
            assertEquals(date, datePickerUtcMillisToLocalDate(date.toDatePickerUtcMillis()))
        }
    }

    @Test
    fun `expense dates stay inside the server business range`() {
        val today = LocalDate.of(2026, 7, 22)

        assertTrue(isAllowedExpenseDate(LocalDate.of(2000, 1, 1), today))
        assertTrue(isAllowedExpenseDate(today, today))
        assertFalse(isAllowedExpenseDate(LocalDate.of(1999, 12, 31), today))
        assertFalse(isAllowedExpenseDate(today.plusDays(1), today))
    }

    @Test
    fun `goal deadline cannot start overdue`() {
        val today = LocalDate.of(2026, 7, 22)

        assertTrue(isAllowedGoalDeadline(null, today))
        assertTrue(isAllowedGoalDeadline(today, today))
        assertFalse(isAllowedGoalDeadline(today.minusDays(1), today))
    }
}
