package com.qolve.fluyo.presentation.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.icons.FluyoIcons

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val EMAIL_AUTH = "email_auth"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val MANUAL_ENTRY = "manual_entry"
    // Optional prefill args (used by voice entry, HU-05) and edit mode (expenseId).
    // Plain "manual_entry" still matches.
    const val MANUAL_ENTRY_ROUTE = "manual_entry?amount={amount}&desc={desc}&src={src}&expenseId={expenseId}"
    fun manualEntryPrefilled(amount: String, desc: String, src: String): String =
        "manual_entry?amount=$amount&desc=${android.net.Uri.encode(desc)}&src=$src"
    fun editExpense(expenseId: String): String = "manual_entry?expenseId=$expenseId"
    const val ALL_EXPENSES = "all_expenses"
    const val SCAN_CONFIRM = "scan_confirm"
    const val SCAN_CONFIRM_ROUTE = "$SCAN_CONFIRM/{uri}"
    fun scanConfirm(encodedUri: String) = "$SCAN_CONFIRM/$encodedUri"
    const val GOAL_CREATE = "goal_create"
    const val MANAGE_CATEGORIES = "manage_categories"

    const val HOME = "home"
    const val STATS = "stats"
    const val GOALS = "goals"
    const val PROFILE = "profile"
}

enum class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    // Outline-only variants — the nav bar adds the coral dot as an overlay so the icon
    // outline auto-tints with LocalContentColor for proper dark-mode contrast.
    Home(Routes.HOME, R.string.nav_home, FluyoIcons.Outline.Home),
    Stats(Routes.STATS, R.string.nav_stats, FluyoIcons.Outline.Stats),
    Goals(Routes.GOALS, R.string.nav_goals, FluyoIcons.Outline.Goals),
    Profile(Routes.PROFILE, R.string.nav_profile, FluyoIcons.Outline.Profile),
}
