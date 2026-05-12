package com.qolve.fluyo.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.qolve.fluyo.R

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

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
    Home(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
    Stats(Routes.STATS, R.string.nav_stats, Icons.Outlined.BarChart),
    Goals(Routes.GOALS, R.string.nav_goals, Icons.Outlined.Flag),
    Profile(Routes.PROFILE, R.string.nav_profile, Icons.Outlined.AccountCircle),
}
