package com.qolve.fluyo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MonthlyBreakdown], including the "ingreso extra del mes" semantics:
 * [MonthlyBreakdown.monthlyBudget] is the EFFECTIVE budget (base + extras) and
 * [MonthlyBreakdown.extraIncome] is display-only — already included in the effective.
 */
class MonthlyBreakdownTest {

    @Test
    fun `baseBudget is effective minus extras`() {
        val b = MonthlyBreakdown(monthlyBudget = 1100.0, totalSpent = 0.0, extraIncome = 200.0)
        assertEquals(900.0, b.baseBudget, 0.001)
    }

    @Test
    fun `percentage and remaining use the effective budget`() {
        val b = MonthlyBreakdown(monthlyBudget = 1000.0, totalSpent = 500.0, extraIncome = 200.0)
        assertEquals(0.5f, b.percentageUsed, 0.001f)
        assertEquals(500.0, b.remaining, 0.001)
    }

    @Test
    fun `extras can flip an over-budget month back under`() {
        val over = MonthlyBreakdown(monthlyBudget = 900.0, totalSpent = 950.0)
        assertTrue(over.isOverBudget)
        // Same spend, but a S/200 extra raised the effective budget to 1100.
        val rescued = MonthlyBreakdown(monthlyBudget = 1100.0, totalSpent = 950.0, extraIncome = 200.0)
        assertFalse(rescued.isOverBudget)
    }

    @Test
    fun `zero budget keeps percentage at zero`() {
        val b = MonthlyBreakdown(monthlyBudget = 0.0, totalSpent = 50.0)
        assertEquals(0f, b.percentageUsed, 0.001f)
    }
}
