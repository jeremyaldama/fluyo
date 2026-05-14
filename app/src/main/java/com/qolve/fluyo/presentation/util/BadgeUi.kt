package com.qolve.fluyo.presentation.util

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType

@StringRes
fun BadgeType.nameRes(): Int = when (this) {
    BadgeType.FIRST_EXPENSE -> R.string.badge_first_expense
    BadgeType.STREAK_7 -> R.string.badge_streak_7
    BadgeType.STREAK_30 -> R.string.badge_streak_30
    BadgeType.FIRST_GOAL -> R.string.badge_first_goal
    BadgeType.SAVER_MONTH -> R.string.badge_saver_month
    BadgeType.MIL_SOLES -> R.string.badge_mil_soles
    BadgeType.NO_YAPE -> R.string.badge_no_yape
    BadgeType.PERFECT_MONTH -> R.string.badge_perfect_month
}

@StringRes
fun BadgeType.descriptionRes(): Int = when (this) {
    BadgeType.FIRST_EXPENSE -> R.string.badge_first_expense_desc
    BadgeType.STREAK_7 -> R.string.badge_streak_7_desc
    BadgeType.STREAK_30 -> R.string.badge_streak_30_desc
    BadgeType.FIRST_GOAL -> R.string.badge_first_goal_desc
    BadgeType.SAVER_MONTH -> R.string.badge_saver_month_desc
    BadgeType.MIL_SOLES -> R.string.badge_mil_soles_desc
    BadgeType.NO_YAPE -> R.string.badge_no_yape_desc
    BadgeType.PERFECT_MONTH -> R.string.badge_perfect_month_desc
}

fun BadgeType.icon(): ImageVector = when (this) {
    BadgeType.FIRST_EXPENSE -> Icons.Outlined.Star
    BadgeType.STREAK_7 -> Icons.Outlined.LocalFireDepartment
    BadgeType.STREAK_30 -> Icons.Outlined.CalendarMonth
    BadgeType.FIRST_GOAL -> Icons.Outlined.Flag
    BadgeType.SAVER_MONTH -> Icons.Outlined.EmojiEvents
    BadgeType.MIL_SOLES -> Icons.Outlined.Diamond
    BadgeType.NO_YAPE -> Icons.Outlined.PhoneAndroid
    BadgeType.PERFECT_MONTH -> Icons.Outlined.WorkspacePremium
}

/**
 * The "expressive" face of a badge — a single emoji glyph. We render emoji directly in
 * the Medallas grid instead of an ImageVector because:
 *   • the redesign brief explicitly shows emoji per badge (mockup 08)
 *   • emoji carry color/personality the user already understands
 *   • a single emoji renders at any size without losing fidelity
 *
 * The icon mapping above is still kept for places that want a monochrome silhouette
 * (e.g. the next-badge callout on the old layout).
 */
fun BadgeType.emoji(): String = when (this) {
    BadgeType.FIRST_EXPENSE -> "🎯"
    BadgeType.STREAK_7 -> "🔥"
    BadgeType.SAVER_MONTH -> "🎉"
    BadgeType.FIRST_GOAL -> "🏆"
    BadgeType.STREAK_30 -> "📅"
    BadgeType.MIL_SOLES -> "💎"
    BadgeType.NO_YAPE -> "💵"
    BadgeType.PERFECT_MONTH -> "⭐"
}

@StringRes
fun levelNameRes(key: String): Int = when (key) {
    "novato" -> R.string.level_novato
    "aprendiz" -> R.string.level_aprendiz
    "organizado" -> R.string.level_organizado
    "experto" -> R.string.level_experto
    "maestro" -> R.string.level_maestro
    else -> R.string.level_novato
}
