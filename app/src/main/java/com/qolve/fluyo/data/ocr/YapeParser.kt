package com.qolve.fluyo.data.ocr

import com.qolve.fluyo.domain.model.DetectedField
import com.qolve.fluyo.domain.model.ParsedReceipt
import com.qolve.fluyo.data.parsing.parseLocalizedMoney
import com.qolve.fluyo.domain.model.MoneyAmount
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
        val note = extractNote(lines)?.also { detected += DetectedField.NOTE }

        return ParsedReceipt(
            amount = amount,
            recipient = recipient,
            date = date,
            note = note,
            rawText = rawText,
            detected = detected,
        )
    }

    private fun extractAmount(rawText: String, lines: List<String>): MoneyAmount? {
        // Strategy: prefer the largest amount on a line that contains "S/" — that's
        // typically the headline total. Fall back to first match anywhere.
        val penRegex = Regex("""S\s*/\s*\.?\s*(\d+(?:[.,]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val bareNumberRegex = Regex("""(\d+[.,]\d{2})""")

        val penMatches = penRegex.findAll(rawText).mapNotNull { match ->
            cleanAmount(match.groupValues[1])
        }.toList()

        if (penMatches.isNotEmpty()) return penMatches.maxOrNull()

        // Fallback: look for a standalone "12.50" style number near the top of the image.
        val topHalf = lines.take((lines.size / 2).coerceAtLeast(3))
        val numericFromTop = topHalf
            .flatMap { line -> bareNumberRegex.findAll(line).toList() }
            .mapNotNull { cleanAmount(it.groupValues[1]) }

        return numericFromTop.maxOrNull()
    }

    private fun cleanAmount(raw: String): MoneyAmount? {
        val onlyDigitsAndSeps = raw.replace(Regex("""[^\d.,]"""), "")
        return parseLocalizedMoney(onlyDigitsAndSeps)
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

        // New Yape layout (2026): "¡Yapeaste!" headline (no trailing "a"), amount, then
        // the recipient alone on its own line right above the date/time row.
        val dateLineIdx = lines.indexOfFirst { isDateOrTimeLine(it) }
        if (dateLineIdx > 0) {
            val above = lines[dateLineIdx - 1]
            if (looksLikeName(above)) return cleanName(above)
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

    // ── Note (the voucher's free-text message chip, e.g. "delicia") ────────────

    private val timeRegex = Regex("""\d{1,2}:\d{2}""")
    // `(?iu)`: the `u` makes case-insensitivity Unicode-aware — plain `(?i)` is
    // ASCII-only in Java, so `[oó]` would not match the "Ó" in "CÓDIGO".
    private val stopLabelRegex = Regex(
        """(?iu)c[oó]digo\s+de\s+seguridad|datos\s+de\s+la\s+transacci|nro\.?\s+de|n[uú]mero\s+de|destino|comisi[oó]n|operaci[oó]n""",
    )

    private fun isDateOrTimeLine(line: String): Boolean =
        timeRegex.containsMatchIn(line) ||
            Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""").containsMatchIn(line) ||
            Regex("""(?i)\d{1,2}\s*(?:de\s+)?[a-záéíóú]{3,12}\.?\s*(?:de(?:l)?\s*)?\d{2,4}""").containsMatchIn(line)

    /**
     * The note chip sits between the date/time row and the security-code / transaction
     * block. Conservative: return the first free-text line in that window, or null —
     * never a label, an amount, a name-cased header, or a digit run (security code).
     */
    private fun extractNote(lines: List<String>): String? {
        val dateIdx = lines.indexOfFirst { isDateOrTimeLine(it) }
        if (dateIdx == -1) return null

        for (i in (dateIdx + 1) until lines.size) {
            val line = lines[i]
            if (stopLabelRegex.containsMatchIn(line)) return null
            val candidate = line.trim()
            val hasLetters = candidate.any { it.isLetter() }
            val isAmountish = candidate.contains("S/") || candidate.contains("S /")
            if (hasLetters && !isAmountish && candidate.length in 2..60) {
                return cleanNote(candidate)
            }
            // Digit runs (security code) or symbols: keep scanning until a stop label.
        }
        return null
    }

    /**
     * The chip's message icon often OCRs as a stray leading letter ("F delicia").
     * Drop a leading single-letter token unless it's a real one-letter Spanish word.
     */
    private fun cleanNote(raw: String): String? {
        var note = raw.trim()
        val tokens = note.split(Regex("""\s+"""))
        if (tokens.size >= 2 && tokens[0].length == 1 &&
            tokens[0].lowercase() !in setOf("a", "e", "o", "u", "y")
        ) {
            note = tokens.drop(1).joinToString(" ")
        }
        return note.take(60).takeIf { it.length >= 2 }
    }

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
        // `\.?` after the month: abbreviated months print with a period ("27 jun. 2026").
        val spanishDateRegex = Regex(
            """(\d{1,2})\s*(?:de\s+)?([a-záéíóú]{3,12})\.?\s*(?:de(?:l)?\s*)?(\d{2,4})""",
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
