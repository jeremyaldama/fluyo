package com.qolve.fluyo.domain.model

enum class NudgeType(val wire: String) {
    PROGRESS("progress"),
    REMINDER("reminder"),
    BUDGET("budget"),
    GOAL("goal");

    companion object {
        fun fromWire(value: String): NudgeType? = entries.firstOrNull { it.wire == value }
    }
}

/** Content the worker uses to build a notification. */
data class NudgeContent(
    val type: NudgeType,
    val title: String,
    val body: String,
)

/** A nudge is valid only while the session generation used to compute it is current. */
data class NudgeDecision(
    val content: NudgeContent,
    val sessionEpoch: Long,
)
