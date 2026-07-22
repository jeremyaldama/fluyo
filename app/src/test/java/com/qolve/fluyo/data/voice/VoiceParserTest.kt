package com.qolve.fluyo.data.voice

import com.qolve.fluyo.domain.model.MoneyAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the HU-05 voice transcript parser (pure Kotlin, no Android deps). */
class VoiceParserTest {

    @Test
    fun `parses amount category and description from a full phrase`() {
        val r = VoiceParser.parse("gasté 15 soles en almuerzo")
        assertEquals(MoneyAmount.ofCents(1_500), r.amount)
        assertEquals("Comida", r.categoryHint)
        assertEquals("almuerzo", r.description)
    }

    @Test
    fun `parses short phrase without a verb`() {
        val r = VoiceParser.parse("20 en taxi")
        assertEquals(MoneyAmount.ofCents(2_000), r.amount)
        assertEquals("Transporte", r.categoryHint)
        assertEquals("taxi", r.description)
    }

    @Test
    fun `parses decimal amount with currency symbol`() {
        val r = VoiceParser.parse("S/ 12.50 en café")
        assertEquals(MoneyAmount.ofCents(1_250), r.amount)
        assertEquals("Snacks", r.categoryHint)
        assertEquals("café", r.description)
    }

    @Test
    fun `parses comma decimal separator`() {
        val r = VoiceParser.parse("gasté 8,90 en cine")
        assertEquals(MoneyAmount.ofCents(890), r.amount)
        assertEquals("Entretenimiento", r.categoryHint)
    }

    @Test
    fun `amount above 999 is not truncated`() {
        val r = VoiceParser.parse("gasté 1200 soles en matrícula")

        assertEquals(MoneyAmount.ofCents(120_000), r.amount)
        assertEquals("Educación", r.categoryHint)
    }

    @Test
    fun `grouped amounts are not mistaken for decimals`() {
        assertEquals(MoneyAmount.ofCents(100_000), VoiceParser.parse("gasté 1,000 soles").amount)
        assertEquals(MoneyAmount.ofCents(100_000), VoiceParser.parse("gasté 1.000 soles").amount)
        assertEquals(MoneyAmount.ofCents(123_456), VoiceParser.parse("gasté 1.234,56 soles").amount)
    }

    @Test
    fun `ignores spelled-out numbers there is no numeral`() {
        val r = VoiceParser.parse("veinte soles de comida")
        assertNull(r.amount)
        assertEquals("Comida", r.categoryHint)
        assertEquals("comida", r.description)
    }

    @Test
    fun `unknown category yields null hint but keeps description`() {
        val r = VoiceParser.parse("30 en lavandería")
        assertEquals(MoneyAmount.ofCents(3_000), r.amount)
        assertNull(r.categoryHint)
        assertEquals("lavandería", r.description)
    }

    @Test
    fun `empty transcript returns all nulls`() {
        val r = VoiceParser.parse("   ")
        assertNull(r.amount)
        assertNull(r.categoryHint)
        assertNull(r.description)
    }
}
