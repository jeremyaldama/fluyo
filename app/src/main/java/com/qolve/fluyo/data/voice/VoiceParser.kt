package com.qolve.fluyo.data.voice

import com.qolve.fluyo.data.parsing.parseLocalizedMoney
import com.qolve.fluyo.domain.model.MoneyAmount

/**
 * Parses a Spanish speech transcript into the pieces needed to pre-fill an expense
 * (HU-05). Conservative like `YapeParser`: returns null fields rather than guessing,
 * because the user confirms on the manual-entry screen afterwards.
 *
 * Examples it handles: "gasté 15 soles en almuerzo", "20 en taxi" and
 * "S/ 12.50 en café". Spelled-out numerals are deliberately left for confirmation.
 */
object VoiceParser {

    data class VoiceParsed(
        val amount: MoneyAmount?,
        val categoryHint: String?,
        val description: String?,
    )

    // First number in the phrase — integer or decimal, comma or dot. Voice input usually
    // dictates digits ("quince" stays as text and is ignored — we only take numerals).
    private val amountRegex = Regex(
        """(?<![\d.,])(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?![\d.,])""",
    )

    // Keyword → canonical default-category name (matches the seed_default_categories list).
    // Word edges use `(?<![\p{L}])…(?![\p{L}])` ("not adjacent to a Unicode letter") so
    // accented keywords (café, menú, útiles) match at boundaries — a plain ASCII `\b` fails
    // next to é/ú/í.
    //
    // DO NOT use the `(?U)` inline flag here. It works on the JVM (so `src/test` stayed
    // green) but Android's ICU-backed regex engine rejects it with PatternSyntaxException,
    // which threw in this object's <clinit> and crashed voice entry the instant parse() ran
    // on a real device. The emulator never caught it because with no mic the recognizer
    // never returns RESULT_OK, so parse() was never called. `\p{L}` is honored by BOTH
    // engines; VoiceParserInstrumentedTest guards this on-device.
    private val categoryKeywords: List<Pair<Regex, String>> = listOf(
        Regex("""(?<![\p{L}])(almuerzo|comida|desayuno|cena|menú|menu|restaurante|comí|comi)(?![\p{L}])""") to "Comida",
        Regex("""(?<![\p{L}])(taxi|bus|uber|combi|pasaje|micro|tren|metro|transporte)(?![\p{L}])""") to "Transporte",
        Regex("""(?<![\p{L}])(cine|película|pelicula|juego|videojuego|concierto|fiesta|entretenimiento)(?![\p{L}])""") to "Entretenimiento",
        Regex("""(?<![\p{L}])(café|cafe|snack|galleta|gaseosa|kiosko|propina)(?![\p{L}])""") to "Snacks",
        Regex("""(?<![\p{L}])(farmacia|medicina|doctor|salud|clínica|clinica|pastilla)(?![\p{L}])""") to "Salud",
        Regex("""(?<![\p{L}])(libro|curso|útiles|utiles|universidad|matrícula|matricula|educación|educacion)(?![\p{L}])""") to "Educación",
    )

    fun parse(transcript: String): VoiceParsed {
        val text = transcript.trim()
        if (text.isEmpty()) return VoiceParsed(null, null, null)
        val lower = text.lowercase()

        val amount = amountRegex.find(lower)
            ?.groupValues?.get(1)
            ?.let { parseLocalizedMoney(it) }

        val categoryHint = categoryKeywords.firstOrNull { it.first.containsMatchIn(lower) }?.second

        // Description: prefer the chunk after "en"/"de " (e.g. "...en almuerzo" → "almuerzo"),
        // else the whole phrase. Keeps the original casing of the captured tail.
        val description = Regex("""\b(?:en|de|para)\s+(.{2,40})$""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
            ?: text

        return VoiceParsed(amount = amount, categoryHint = categoryHint, description = description)
    }
}
