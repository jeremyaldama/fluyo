package com.qolve.fluyo.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.qolve.fluyo.presentation.theme.BrandDot
import com.qolve.fluyo.presentation.theme.CoralRamp500

/**
 * Fluyo bottom navigation.
 *
 * **Two-layer icon rendering.** Material's `Icon` flattens a vector to a single tint color
 * — useful for theme-aware ink, fatal for our two-tone glyphs. We split each nav icon:
 *
 *   1. The outline (`FluyoIcons.Outline.*`) renders through `Icon` so its color follows
 *      `LocalContentColor.current` — dark text on light surface in light mode, light text
 *      on dark surface in dark mode.
 *   2. A separate [BrandDot] overlay sits in the top-right corner of the icon's bounding
 *      box, painted in static coral. The dot stays brand-true regardless of theme state
 *      or selection state.
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
    Box(modifier = Modifier.size(24.dp)) {
        Icon(
            imageVector = icon.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        // Coral accent dot anchored to the top-right of the icon's bounding box. The
        // negative offsets pull it slightly above + outside so it reads as a stamp on the
        // glyph rather than a colored pixel inside it.
        BrandDot(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp),
            size = 5.dp,
            color = CoralRamp500,
        )
    }
}
