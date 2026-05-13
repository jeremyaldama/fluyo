package com.qolve.fluyo.domain.model

import java.time.LocalDate

/** Result of running OCR + parsing on a receipt/Yape screenshot. */
data class ParsedReceipt(
    val amount: Double? = null,
    val recipient: String? = null,
    val date: LocalDate? = null,
    val rawText: String = "",
    val detected: Set<DetectedField> = emptySet(),
)

enum class DetectedField { AMOUNT, RECIPIENT, DATE }
