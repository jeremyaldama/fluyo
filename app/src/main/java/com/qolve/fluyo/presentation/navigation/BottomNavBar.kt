package com.qolve.fluyo.presentation.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.qolve.fluyo.presentation.theme.NeutralRamp500
import com.qolve.fluyo.presentation.theme.TealRamp100
import com.qolve.fluyo.presentation.theme.TealRamp500

/**
 * Fluyo bottom navigation.
 *
 * Two design choices worth flagging:
 *
 *   1. We render each tab's glyph through [Image] rather than [androidx.compose.material3.Icon].
 *      Material's `Icon` applies `LocalContentColor` as an `SrcIn` tint over the entire
 *      vector, which would flatten our two-color glyphs (black outline + coral accent dot)
 *      down to a single hue. `Image` honors the colors baked into the `ImageVector` — so
 *      the coral pin stays coral regardless of selection state.
 *
 *   2. Selection state is communicated entirely by the mint indicator pill *behind* the
 *      icon and the teal label color. The icon itself stays unchanged across states. This
 *      matches the Fluyo design system (geometric, low-chrome) and keeps the brand-dot
 *      motif visible at all times.
 */
@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                icon = {
                    Image(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = TealRamp500,
                    unselectedTextColor = NeutralRamp500,
                    indicatorColor = TealRamp100,
                ),
            )
        }
    }
}
