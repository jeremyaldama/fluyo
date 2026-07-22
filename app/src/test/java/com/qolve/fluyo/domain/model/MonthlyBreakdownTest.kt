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
        val b = MonthlyBreakdown(money(1100), MoneyAmount.ZERO, money(200))
        assertEquals(money(900), b.baseBudget)
    }

    @Test
    fun `percentage and remaining use the effective budget`() {
        val b = MonthlyBreakdown(money(1000), money(500), money(200))
        assertEquals(0.5f, b.percentageUsed, 0.001f)
        assertEquals(money(500), b.remaining)
    }

    @Test
    fun `extras can flip an over-budget month back under`() {
        val over = MonthlyBreakdown(money(900), money(950))
        assertTrue(over.isOverBudget)
        // Same spend, but a S/200 extra raised the effective budget to 1100.
        val rescued = MonthlyBreakdown(money(1100), money(950), money(200))
        assertFalse(rescued.isOverBudget)
    }

    @Test
    fun `zero budget keeps percentage at zero`() {
        val b = MonthlyBreakdown(MoneyAmount.ZERO, money(50))
        assertEquals(0f, b.percentageUsed, 0.001f)
    }

    private fun money(major: Long) = MoneyAmount.ofCents(Math.multiplyExact(major, 100L))
}
