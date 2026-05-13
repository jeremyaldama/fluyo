package com.qolve.fluyo.domain.model

enum class BadgeType(val wire: String, val points: Int) {
    FIRST_EXPENSE("first_expense", 1),
    STREAK_7("streak_7", 5),
    STREAK_30("streak_30", 20),
    FIRST_GOAL("first_goal", 10),
    SAVER_MONTH("saver_month", 15);

    companion object {
        fun fromWire(value: String): BadgeType? = entries.firstOrNull { it.wire == value }
    }
}
