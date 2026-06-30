package com.qolve.fluyo.data.voice

/**
 * Parses a Spanish speech transcript into the pieces needed to pre-fill an expense
 * (HU-05). Conservative like `YapeParser`: returns null fields rather than guessing,
 * because the user confirms on the manual-entry screen afterwards.
 *
 * Examples it handles: "gasté 15 soles en almuerzo", "20 en taxi", "veinte soles de
 * comida", "S/ 12.50 en café".
 */
object VoiceParser {

    data class VoiceParsed(
        val amount: Double?,
        val categoryHint: String?,
        val description: String?,
    )

    // First number in the phrase — integer or decimal, comma or dot. Voice input usually
    // dictates digits ("quince" stays as text and is ignored — we only take numerals).
    private val amountRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)""")

    // Keyword → canonical default-category name (matches the seed_default_categories list).
    private val categoryKeywords: List<Pair<Regex, String>> = listOf(
        Regex("""\b(almuerzo|comida|desayuno|cena|menú|menu|restaurante|comí|comi)\b""") to "Comida",
        Regex("""\b(taxi|bus|uber|combi|pasaje|micro|tren|metro|transporte)\b""") to "Transporte",
        Regex("""\b(cine|película|pelicula|juego|videojuego|concierto|fiesta|entretenimiento)\b""") to "Entretenimiento",
        Regex("""\b(café|cafe|snack|galleta|gaseosa|kiosko|propina)\b""") to "Snacks",
        Regex("""\b(farmacia|medicina|doctor|salud|clínica|clinica|pastilla)\b""") to "Salud",
        Regex("""\b(libro|curso|útiles|utiles|universidad|matrícula|matricula|educación|educacion)\b""") to "Educación",
    )

    fun parse(transcript: String): VoiceParsed {
        val text = transcript.trim()
        if (text.isEmpty()) return VoiceParsed(null, null, null)
        val lower = text.lowercase()

        val amount = amountRegex.find(lower)
            ?.groupValues?.get(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }

        val categoryHint = categoryKeywords.firstOrNull { it.first.containsMatchIn(lower) }?.second

        // Description: prefer the chunk after "en"/"de " (e.g. "...en almuerzo" → "almuerzo"),
        // else the whole phrase. Keeps the original casing of the captured tail.
        val description = Regex("""\b(?:en|de|para)\s+(.{2,40})$""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
            ?: text

        return VoiceParsed(amount = amount, categoryHint = categoryHint, description = description)
    }
}
