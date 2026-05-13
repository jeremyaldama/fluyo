package com.qolve.fluyo.presentation.util

import java.text.NumberFormat
import java.util.Locale

private val LOCALE_ES_PE: Locale = Locale.forLanguageTag("es-PE")

private val penFormat: NumberFormat = NumberFormat.getNumberInstance(LOCALE_ES_PE).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

/** Format as "S/ 15.50". */
fun formatPen(amount: Double): String = "S/ ${penFormat.format(amount)}"
