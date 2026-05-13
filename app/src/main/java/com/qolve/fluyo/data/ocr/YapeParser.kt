package com.qolve.fluyo.data.ocr

import com.qolve.fluyo.domain.model.DetectedField
import com.qolve.fluyo.domain.model.ParsedReceipt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort extraction of amount/recipient/date from Yape/Plin/receipt OCR text.
 * Conservative by design: prefer leaving a field null over guessing wrong, since the
 * confirm screen lets the user fix anything.
 */
@Singleton
class YapeParser @Inject constructor() {

    fun parse(rawText: String): ParsedReceipt {
        if (rawText.isBlank()) return ParsedReceipt(rawText = rawText)

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val detected = mutableSetOf<DetectedField>()

        val amount = extractAmount(rawText, lines)?.also { detected += DetectedField.AMOUNT }
        val recipient = extractRecipient(lines)?.also { detected += DetectedField.RECIPIENT }
        val date = extractDate(rawText)?.also { detected += DetectedField.DATE }

        return ParsedReceipt(
            amount = amount,
            recipient = recipient,
            date = date,
            rawText = rawText,
            detected = detected,
        )
    }

    private fun extractAmount(rawText: String, lines: List<String>): Double? {
        // Strategy: prefer the largest amount on a line that contains "S/" — that's
        // typically the headline total. Fall back to first match anywhere.
        val penRegex = Regex("""S\s*/\s*\.?\s*(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val bareNumberRegex = Regex("""(\d+[.,]\d{2})""")

        val penMatches = penRegex.findAll(rawText).mapNotNull { match ->
            cleanAmount(match.groupValues[1])
        }.toList()

        if (penMatches.isNotEmpty()) return penMatches.max()

        // Fallback: look for a standalone "12.50" style number near the top of the image.
        val topHalf = lines.take((lines.size / 2).coerceAtLeast(3))
        val numericFromTop = topHalf
            .flatMap { line -> bareNumberRegex.findAll(line).toList() }
            .mapNotNull { cleanAmount(it.groupValues[1]) }

        return numericFromTop.maxOrNull()
    }

    private fun cleanAmount(raw: String): Double? {
        // Normalize: Latin uses both '.' and ',' as decimal separators. Strip thousands
        // separators, keep the last separator as decimal.
        val onlyDigitsAndSeps = raw.replace(Regex("""[^\d.,]"""), "")
        val lastSep = onlyDigitsAndSeps.lastIndexOfAny(charArrayOf('.', ','))
        val normalized = if (lastSep == -1) {
            onlyDigitsAndSeps
        } else {
            val intPart = onlyDigitsAndSeps.substring(0, lastSep).replace(Regex("""[.,]"""), "")
            val decPart = onlyDigitsAndSeps.substring(lastSep + 1)
            "$intPart.$decPart"
        }
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 && it < 1_000_000 }
    }

    private fun extractRecipient(lines: List<String>): String? {
        // Yape commonly prints:
        //   "Yapeaste a"
        //   "Juan Perez"   <-- next line
        //
        // Plin:
        //   "Le enviaste a"
        //   "Maria Lopez"
        //
        // Also: "Pagaste a [NAME]" inline.
        val triggerRegexes = listOf(
            Regex("""yapeaste\s+a""", RegexOption.IGNORE_CASE),
            Regex("""le\s+enviaste\s+a""", RegexOption.IGNORE_CASE),
            Regex("""pagaste\s+a""", RegexOption.IGNORE_CASE),
            Regex("""enviado\s+a""", RegexOption.IGNORE_CASE),
        )

        lines.forEachIndexed { index, line ->
            triggerRegexes.forEach { rx ->
                val match = rx.find(line)
                if (match != null) {
                    // Inline form: "Pagaste a Juan Perez"
                    val after = line.substring(match.range.last + 1).trim()
                    if (after.isNotEmpty() && looksLikeName(after)) {
                        return cleanName(after)
                    }
                    // Otherwise look at the next line.
                    val next = lines.getOrNull(index + 1)?.trim().orEmpty()
                    if (looksLikeName(next)) return cleanName(next)
                }
            }
        }
        return null
    }

    private fun looksLikeName(text: String): Boolean {
        if (text.isBlank() || text.length < 2) return false
        if (text.contains("S/") || text.contains("S /")) return false
        if (Regex("""\d""").containsMatchIn(text)) return false
        // At least one capitalized word
        return text.split(Regex("""\s+""")).any { word ->
            word.length >= 2 && word[0].isUpperCase()
        }
    }

    private fun cleanName(text: String): String =
        text.replace(Regex("""[^\p{L}\s]"""), "").trim().take(80)

    private fun extractDate(rawText: String): LocalDate? {
        // Try common Latin-American formats: "DD/MM/YYYY", "DD-MM-YYYY", and Spanish month names.
        val numericFormats = listOf(
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-PE")),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.forLanguageTag("es-PE")),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.forLanguageTag("es-PE")),
        )

        val numericRegex = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""")
        numericRegex.findAll(rawText).forEach { match ->
            for (fmt in numericFormats) {
                runCatching { return LocalDate.parse(match.groupValues[1], fmt) }
            }
        }

        // Spanish month names: "12 de mayo del 2026" or "12 may 2026"
        val months = mapOf(
            "ene" to 1, "enero" to 1,
            "feb" to 2, "febrero" to 2,
            "mar" to 3, "marzo" to 3,
            "abr" to 4, "abril" to 4,
            "may" to 5, "mayo" to 5,
            "jun" to 6, "junio" to 6,
            "jul" to 7, "julio" to 7,
            "ago" to 8, "agosto" to 8,
            "sep" to 9, "septiembre" to 9, "set" to 9, "setiembre" to 9,
            "oct" to 10, "octubre" to 10,
            "nov" to 11, "noviembre" to 11,
            "dic" to 12, "diciembre" to 12,
        )
        val spanishDateRegex = Regex(
            """(\d{1,2})\s*(?:de\s+)?([a-záéíóú]{3,12})\s*(?:de(?:l)?\s*)?(\d{2,4})""",
            RegexOption.IGNORE_CASE,
        )
        spanishDateRegex.findAll(rawText).forEach { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthName = match.groupValues[2].lowercase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u")
            val year = match.groupValues[3].toIntOrNull()?.let {
                if (it < 100) 2000 + it else it
            }
            val month = months[monthName] ?: months[monthName.take(3)]
            if (day != null && month != null && year != null) {
                runCatching { return LocalDate.of(year, month, day) }
            }
        }

        return null
    }
}
