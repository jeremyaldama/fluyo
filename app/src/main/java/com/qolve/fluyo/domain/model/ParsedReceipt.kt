package com.qolve.fluyo.domain.model

import java.time.LocalDate

/** Result of running OCR + parsing on a receipt/Yape screenshot. */
data class ParsedReceipt(
    val amount: MoneyAmount? = null,
    val recipient: String? = null,
    val date: LocalDate? = null,
    /** The voucher's free-text message chip (e.g. "delicia") — prefills the description. */
    val note: String? = null,
    val rawText: String = "",
    val detected: Set<DetectedField> = emptySet(),
)

enum class DetectedField { AMOUNT, RECIPIENT, DATE, NOTE }
