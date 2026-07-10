package com.qolve.fluyo.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [YapeParser] over real voucher layouts. The "new" layout (2026) prints
 * "¡Yapeaste!" as a headline with the recipient on its own line under the amount, plus an
 * optional note chip between the date row and the security-code block.
 */
class YapeParserTest {

    private val parser = YapeParser()

    /** OCR text of the new-layout voucher (as in the reference screenshot). */
    private val newLayoutVoucher = """
        yape
        ¡Yapeaste!
        S/ 6
        Luis Mon*
        27 jun. 2026 | 07:54 p. m.
        delicia
        CÓDIGO DE SEGURIDAD
        0 6 6
        DATOS DE LA TRANSACCIÓN
        Nro. de celular
        *** *** 655
        Destino
        Yape
        Nro. de operación
        28874066
    """.trimIndent()

    @Test
    fun `new layout - extracts amount, recipient, date and note`() {
        val r = parser.parse(newLayoutVoucher)
        assertEquals(6.0, r.amount!!, 0.001)
        assertEquals("Luis Mon", r.recipient)
        assertEquals(LocalDate.of(2026, 6, 27), r.date)
        assertEquals("delicia", r.note)
    }

    @Test
    fun `new layout without note chip - note is null`() {
        val raw = """
            ¡Yapeaste!
            S/ 12.50
            Maria Lopez
            3 ene. 2026 | 10:12 a. m.
            CÓDIGO DE SEGURIDAD
            1 2 3
        """.trimIndent()
        val r = parser.parse(raw)
        assertEquals(12.5, r.amount!!, 0.001)
        assertEquals("Maria Lopez", r.recipient)
        assertNull(r.note)
    }

    @Test
    fun `old layout - trigger phrase recipient still works`() {
        val raw = """
            Yapeaste a
            Juan Perez
            S/ 25.50
            12/05/2026
        """.trimIndent()
        val r = parser.parse(raw)
        assertEquals(25.5, r.amount!!, 0.001)
        assertEquals("Juan Perez", r.recipient)
        assertEquals(LocalDate.of(2026, 5, 12), r.date)
    }

    @Test
    fun `note never captures labels or digit runs`() {
        val raw = """
            ¡Yapeaste!
            S/ 8
            Ana Torres
            15 feb. 2026 | 09:00 p. m.
            0 6 6
            CÓDIGO DE SEGURIDAD
        """.trimIndent()
        assertNull(parser.parse(raw).note)
    }

    @Test
    fun `note strips the chip icon OCR'd as a stray leading letter`() {
        // The message-chip icon often reads as a lone letter: "F delicia".
        val raw = """
            ¡Yapeaste!
            S/ 6
            Luis Mon*
            27 jun. 2026 | 07:54 p. m.
            F delicia
            CÓDIGO DE SEGURIDAD
        """.trimIndent()
        assertEquals("delicia", parser.parse(raw).note)
    }

    @Test
    fun `note keeps real one-letter Spanish words`() {
        val raw = """
            ¡Yapeaste!
            S/ 20
            Ana Torres
            1 mar. 2026 | 08:00 a. m.
            a cuenta del almuerzo
            CÓDIGO DE SEGURIDAD
        """.trimIndent()
        assertEquals("a cuenta del almuerzo", parser.parse(raw).note)
    }

    @Test
    fun `blank input parses to empty receipt`() {
        val r = parser.parse("")
        assertNull(r.amount)
        assertNull(r.recipient)
        assertNull(r.note)
    }
}
