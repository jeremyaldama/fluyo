package com.qolve.fluyo.presentation.util

import com.qolve.fluyo.domain.model.MoneyAmount

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the multi-currency formatting helpers (HU-11). */
class MoneyTest {

    @Test
    fun `symbol resolves for supported currencies`() {
        assertEquals("S/", currencySymbolFor("PEN"))
        assertEquals("$", currencySymbolFor("USD"))
        assertEquals("€", currencySymbolFor("EUR"))
    }

    @Test
    fun `unknown currency falls back to PEN symbol`() {
        assertEquals("S/", currencySymbolFor("XYZ"))
    }

    @Test
    fun `formatAmount always shows two decimals`() {
        assertEquals("5.00", formatAmount(MoneyAmount.ofCents(500L)))
        assertEquals("15.50", formatAmount(MoneyAmount.ofCents(1_550L)))
    }

    @Test
    fun `formatMoney prefixes the currency symbol`() {
        val amount = MoneyAmount.ofCents(1_550L)
        assertEquals("S/ 15.50", formatMoney(amount, "PEN"))
        assertEquals("$ 15.50", formatMoney(amount, "USD"))
        assertEquals("€ 15.50", formatMoney(amount, "EUR"))
    }

    @Test
    fun `formatPen is the PEN default`() {
        assertEquals("S/ 0.00", formatPen(MoneyAmount.ZERO))
        assertEquals("S/ 99.90", formatPen(MoneyAmount.ofCents(9_990L)))
    }

    @Test
    fun `sanitizeDecimalInput keeps digits and one separator with two decimals`() {
        assertEquals("15.50", sanitizeDecimalInput("15,50"))
        assertEquals("15.50", sanitizeDecimalInput("15.505"))
        assertEquals("1200", sanitizeDecimalInput("S/ 1200"))
        assertEquals("9.99", sanitizeDecimalInput("9.9.9"))
        assertEquals("", sanitizeDecimalInput("abc"))
    }
}
