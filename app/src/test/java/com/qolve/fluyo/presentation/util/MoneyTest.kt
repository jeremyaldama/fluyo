package com.qolve.fluyo.presentation.util

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
        assertEquals("5.00", formatAmount(5.0))
        assertEquals("15.50", formatAmount(15.5))
    }

    @Test
    fun `formatMoney prefixes the currency symbol`() {
        assertEquals("S/ 15.50", formatMoney(15.5, "PEN"))
        assertEquals("$ 15.50", formatMoney(15.5, "USD"))
        assertEquals("€ 15.50", formatMoney(15.5, "EUR"))
    }

    @Test
    fun `formatPen is the PEN default`() {
        assertEquals("S/ 0.00", formatPen(0.0))
        assertEquals("S/ 99.90", formatPen(99.9))
    }
}
