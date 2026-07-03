package com.qolve.fluyo.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the HU-05 voice transcript parser (pure Kotlin, no Android deps). */
class VoiceParserTest {

    @Test
    fun `parses amount category and description from a full phrase`() {
        val r = VoiceParser.parse("gasté 15 soles en almuerzo")
        assertEquals(15.0, r.amount!!, 0.001)
        assertEquals("Comida", r.categoryHint)
        assertEquals("almuerzo", r.description)
    }

    @Test
    fun `parses short phrase without a verb`() {
        val r = VoiceParser.parse("20 en taxi")
        assertEquals(20.0, r.amount!!, 0.001)
        assertEquals("Transporte", r.categoryHint)
        assertEquals("taxi", r.description)
    }

    @Test
    fun `parses decimal amount with currency symbol`() {
        val r = VoiceParser.parse("S/ 12.50 en café")
        assertEquals(12.50, r.amount!!, 0.001)
        assertEquals("Snacks", r.categoryHint)
        assertEquals("café", r.description)
    }

    @Test
    fun `parses comma decimal separator`() {
        val r = VoiceParser.parse("gasté 8,90 en cine")
        assertEquals(8.90, r.amount!!, 0.001)
        assertEquals("Entretenimiento", r.categoryHint)
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
        assertEquals(30.0, r.amount!!, 0.001)
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
