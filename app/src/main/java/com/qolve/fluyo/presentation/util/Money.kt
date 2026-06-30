package com.qolve.fluyo.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.util.Locale

private val LOCALE_ES_PE: Locale = Locale.forLanguageTag("es-PE")

private val amountFormat: NumberFormat = NumberFormat.getNumberInstance(LOCALE_ES_PE).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

/**
 * Currencies the app can display (HU-11). Default is PEN — the thesis target market —
 * but the user can switch in Profile. The map is the single source of truth for both the
 * picker and symbol rendering. Codes are ISO-4217.
 */
val SUPPORTED_CURRENCIES: Map<String, String> = linkedMapOf(
    "PEN" to "S/",
    "USD" to "$",
    "EUR" to "€",
)

/** Symbol for a currency code, falling back to PEN's "S/" for anything unknown. */
fun currencySymbolFor(code: String): String = SUPPORTED_CURRENCIES[code] ?: "S/"

/** Format a bare amount (no symbol), e.g. "1,250.50" in es-PE grouping. */
fun formatAmount(amount: Double): String = amountFormat.format(amount)

/** Format with an explicit currency code, e.g. "S/ 15.50" / "$ 15.50". */
fun formatMoney(amount: Double, code: String): String =
    "${currencySymbolFor(code)} ${amountFormat.format(amount)}"

/**
 * Holds the active currency *symbol* for the composition. Provided once near the nav root
 * (see `FluyoNavHost`) from the signed-in user's currency, so every screen renders amounts
 * in the chosen currency without threading it through every call site. Defaults to "S/".
 */
val LocalCurrencySymbol = compositionLocalOf { "S/" }

/** The active currency symbol for the current composition. */
@Composable
@ReadOnlyComposable
fun currencySymbol(): String = LocalCurrencySymbol.current

/** Format an amount in the composition's active currency, e.g. "S/ 15.50". */
@Composable
@ReadOnlyComposable
fun money(amount: Double): String = "${LocalCurrencySymbol.current} ${amountFormat.format(amount)}"

/**
 * Format as "S/ 15.50" in PEN. Retained for non-composable callers and as the safe default;
 * composables should prefer [money] so the amount honors the user's selected currency.
 */
fun formatPen(amount: Double): String = "S/ ${amountFormat.format(amount)}"
