package com.qolve.fluyo.presentation.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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

/**
 * Fluyo bottom navigation.
 *
 * Icons render through `Icon` so their color follows `LocalContentColor.current` — dark
 * text on light surface in light mode, light text on dark surface in dark mode. No coral
 * [com.qolve.fluyo.presentation.theme.BrandDot] here on purpose: a dot over a nav icon is
 * Android's notification-badge convention, and a permanent decorative one reads as "you
 * have something pending" on all four tabs. The brand dot lives on the logo and the FAB.
 *
 * **Selection state** comes from the colors below — selected gets the brand primary text +
 * the soft primary-container indicator pill; unselected uses `onSurfaceVariant`. Both flip
 * cleanly between modes because they're sourced from `MaterialTheme.colorScheme`.
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
                icon = { NavGlyph(icon = tab) },
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
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    // primaryContainer adapts: light mint in light, deep teal in dark.
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun NavGlyph(icon: BottomTab) {
    Icon(
        imageVector = icon.icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}
