package com.qolve.fluyo.domain.model

enum class GoalStatus(val wire: String) {
    ACTIVE("active"),
    COMPLETED("completed");

    companion object {
        fun fromWire(value: String): GoalStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}
