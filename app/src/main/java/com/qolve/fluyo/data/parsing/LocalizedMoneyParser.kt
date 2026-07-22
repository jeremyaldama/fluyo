package com.qolve.fluyo.data.parsing

import com.qolve.fluyo.domain.model.MoneyAmount
import java.math.RoundingMode

/**
 * Parses OCR/speech numerals without guessing that a three-digit suffix is cents.
 *
 * Both `1,234.56` and `1.234,56` are accepted. A lone separator followed by exactly
 * three digits is treated as grouping (`1,000` = one thousand), while one or two final
 * digits are decimal cents. Malformed grouping is rejected instead of silently changing
 * the amount.
 */
internal fun parseLocalizedMoney(raw: String): MoneyAmount? {
    val value = raw.trim()
    if (!Regex("^[0-9]+(?:[.,][0-9]+)*${'$'}").matches(value)) return null

    val separatorPositions = value.indices.filter { value[it] == '.' || value[it] == ',' }
    val normalized = if (separatorPositions.isEmpty()) {
        value
    } else {
        val lastSeparator = separatorPositions.last()
        val trailingDigits = value.length - lastSeparator - 1
        val decimalSeparator = lastSeparator.takeIf { trailingDigits in 1..2 }
        val integerPart = value.substring(0, decimalSeparator ?: value.length)
        val integerGroups = integerPart.split('.', ',')
        if (integerGroups.any(String::isEmpty)) return null
        if (integerGroups.size > 1 && integerGroups.drop(1).any { it.length != 3 }) return null

        val integerDigits = integerGroups.joinToString(separator = "")
        if (decimalSeparator == null) {
            // Every suffix must be a real grouping block when there is no decimal part.
            if (value.substring(lastSeparator + 1).length != 3) return null
            integerDigits
        } else {
            val decimalDigits = value.substring(decimalSeparator + 1)
            "$integerDigits.$decimalDigits"
        }
    }

    return runCatching {
        MoneyAmount.fromMajor(normalized.toBigDecimal(), RoundingMode.UNNECESSARY)
    }.getOrNull()?.takeIf { amount ->
        amount > MoneyAmount.ZERO && amount.cents <= MAX_SUPPORTED_CENTS
    }
}

private const val MAX_SUPPORTED_CENTS = 9_999_999_999L // 99,999,999.99
