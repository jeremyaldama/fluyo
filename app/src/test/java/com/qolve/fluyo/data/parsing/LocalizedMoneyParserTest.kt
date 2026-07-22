package com.qolve.fluyo.data.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalizedMoneyParserTest {
    @Test
    fun `parses decimal and grouping conventions exactly`() {
        assertEquals(1_000_00L, parseLocalizedMoney("1,000")?.cents)
        assertEquals(1_000_00L, parseLocalizedMoney("1.000")?.cents)
        assertEquals(123_456L, parseLocalizedMoney("1,234.56")?.cents)
        assertEquals(123_456L, parseLocalizedMoney("1.234,56")?.cents)
        assertEquals(850L, parseLocalizedMoney("8,50")?.cents)
    }

    @Test
    fun `rejects malformed ambiguous and unsupported values`() {
        assertNull(parseLocalizedMoney("1,00,000"))
        assertNull(parseLocalizedMoney("1.2.34"))
        assertNull(parseLocalizedMoney("0"))
        assertNull(parseLocalizedMoney("100000000"))
    }
}
