package com.qolve.fluyo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class MoneyAmountTest {

    @Test
    fun `decimal addition is exact`() {
        val tenCents = MoneyAmount.fromMajor(BigDecimal("0.10"), RoundingMode.UNNECESSARY)
        val twentyCents = MoneyAmount.fromMajor(BigDecimal("0.20"), RoundingMode.UNNECESSARY)

        assertEquals(MoneyAmount.ofCents(30L), tenCents + twentyCents)
        assertEquals(BigDecimal("0.30"), (tenCents + twentyCents).toBigDecimal())
    }

    @Test
    fun `rounding policy is explicit and deterministic`() {
        val tie = BigDecimal("1.005")

        assertEquals(MoneyAmount.ofCents(100L), MoneyAmount.fromMajor(tie, RoundingMode.HALF_EVEN))
        assertEquals(MoneyAmount.ofCents(101L), MoneyAmount.fromMajor(tie, RoundingMode.HALF_UP))
        assertThrows(ArithmeticException::class.java) {
            MoneyAmount.fromMajor(tie, RoundingMode.UNNECESSARY)
        }
    }

    @Test
    fun `transport conversion rounds immediately and rejects non finite values`() {
        assertEquals(
            MoneyAmount.ofCents(10L),
            MoneyAmount.fromTransport(0.1, RoundingMode.HALF_EVEN),
        )
        assertThrows(IllegalArgumentException::class.java) {
            MoneyAmount.fromTransport(Double.NaN, RoundingMode.HALF_EVEN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyAmount.fromTransport(Double.POSITIVE_INFINITY, RoundingMode.HALF_EVEN)
        }
    }

    @Test
    fun `negation and transport output preserve the exact cent value`() {
        val amount = MoneyAmount.ofCents(123L)

        assertEquals(MoneyAmount.ofCents(-123L), -amount)
        assertEquals(1.23, amount.toTransportDouble(), 0.0)
    }

    @Test
    fun `parse accepts decimal separators but rejects ambiguous and overflowing input`() {
        assertEquals(
            MoneyAmount.ofCents(123_456L),
            MoneyAmount.parse("1234,56", RoundingMode.UNNECESSARY),
        )
        assertNull(MoneyAmount.parse("1,234.56", RoundingMode.UNNECESSARY))
        assertNull(MoneyAmount.parse("1e3", RoundingMode.UNNECESSARY))
        assertNull(MoneyAmount.parse("NaN", RoundingMode.UNNECESSARY))
        assertNull(MoneyAmount.parse("999999999999999999999.99", RoundingMode.UNNECESSARY))
    }

    @Test
    fun `checked sum fails on overflow`() {
        val values = listOf(MoneyAmount.ofCents(Long.MAX_VALUE), MoneyAmount.ofCents(1L))

        assertThrows(ArithmeticException::class.java) { values.sumMoney() }
    }

    @Test
    fun `division rounds to cents only at the requested boundary`() {
        assertEquals(
            MoneyAmount.ofCents(33L),
            MoneyAmount.ofCents(100L).dividedBy(3L, RoundingMode.HALF_EVEN),
        )
        assertThrows(IllegalArgumentException::class.java) {
            MoneyAmount.ofCents(100L).dividedBy(0L, RoundingMode.HALF_EVEN)
        }
    }
}
