package com.qolve.fluyo.domain.model

/**
 * Catalog of badges the user can unlock.
 *
 * Order matters: the entries are rendered left-to-right, top-to-bottom in the Medallas
 * grid on Profile, so put the most achievable badges first and aspirational ones last.
 * `wire` is the lowercase snake_case used in Supabase's `badges.badge_type` text column.
 * `points` is the XP awarded when unlocked — totals roll up into `users.total_points`.
 *
 * [com.qolve.fluyo.domain.usecase.BadgeEngine] selects candidates after relevant writes;
 * unlock criteria are evaluated authoritatively by the database RPC.
 * Keep this catalog, the engine rules and `unlock_badge`'s wire-name mapping aligned.
 */
enum class BadgeType(val wire: String, val points: Int) {
    FIRST_EXPENSE("first_expense", 1),
    STREAK_7("streak_7", 5),
    SAVER_MONTH("saver_month", 15),
    FIRST_GOAL("first_goal", 10),
    STREAK_30("streak_30", 20),
    MIL_SOLES("mil_soles", 25),
    /** Legacy wire name; the criterion is seven tracked days without OCR, not a payment method. */
    NO_YAPE("no_yape", 15),
    PERFECT_MONTH("perfect_month", 50);

    companion object {
        fun fromWire(value: String): BadgeType? = entries.firstOrNull { it.wire == value }
    }
}
