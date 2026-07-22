package com.qolve.fluyo.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {

    @Test
    fun `neutralizes spreadsheet formulas including after whitespace`() {
        assertEquals("'=1+1", escapeCsvCell("=1+1"))
        assertEquals("'  +1+1", escapeCsvCell("  +1+1"))
        assertEquals("'@SUM(A1:A2)", escapeCsvCell("@SUM(A1:A2)"))
        assertEquals("'-10", escapeCsvCell("-10"))
        assertEquals("\"'\r\n=1+1\"", escapeCsvCell("\r\n=1+1"))
        assertEquals("'\u000B+1+1", escapeCsvCell("\u000B+1+1"))
    }

    @Test
    fun `applies RFC style quoting after formula neutralization`() {
        assertEquals("\"'=cmd,with comma\"", escapeCsvCell("=cmd,with comma"))
        assertEquals("\"a \"\"quote\"\"\"", escapeCsvCell("a \"quote\""))
        assertEquals("\"first\nsecond\"", escapeCsvCell("first\nsecond"))
    }

    @Test
    fun `leaves ordinary cells unchanged`() {
        assertEquals("Mercado", escapeCsvCell("Mercado"))
        assertEquals("15.50", escapeCsvCell("15.50"))
        assertEquals("2026-07-22", escapeCsvCell("2026-07-22"))
    }
}
