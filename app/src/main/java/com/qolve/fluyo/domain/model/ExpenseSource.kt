package com.qolve.fluyo.domain.model

enum class ExpenseSource(val wire: String) {
    MANUAL("manual"),
    OCR("ocr"),
    VOICE("voice"),
    WHATSAPP("whatsapp"),
    EMAIL("email");

    companion object {
        fun fromWire(value: String): ExpenseSource =
            entries.firstOrNull { it.wire == value } ?: MANUAL
    }
}
