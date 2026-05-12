package com.qolve.fluyo.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qolve.fluyo.presentation.screens.auth.LoginScreen
import com.qolve.fluyo.presentation.screens.goals.GoalsPlaceholder
import com.qolve.fluyo.presentation.screens.home.HomePlaceholder
import com.qolve.fluyo.presentation.screens.onboarding.OnboardingHost
import com.qolve.fluyo.presentation.screens.profile.ProfilePlaceholder
import com.qolve.fluyo.presentation.screens.stats.StatsPlaceholder

@Composable
fun FluyoNavHost(
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val state by rootViewModel.uiState.collectAsStateWithLifecycle()
    val rootNav = rememberNavController()

    LaunchedEffect(state.startRoute) {
        state.startRoute?.let { route ->
            rootNav.navigate(route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = rootNav,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) { SplashRoute() }
        composable(Routes.LOGIN) {
            LoginScreen(composeAuth = rootViewModel.composeAuth)
        }
        composable(Routes.ONBOARDING) {
            OnboardingHost(onFinished = { rootViewModel.markOnboardingDone() })
        }
        composable(Routes.MAIN) {
            MainShell()
        }
    }
}

@Composable
private fun SplashRoute() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainShell() {
    val nav: NavHostController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavBar(nav) },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.HOME) { HomePlaceholder() }
            composable(Routes.STATS) { StatsPlaceholder() }
            composable(Routes.GOALS) { GoalsPlaceholder() }
            composable(Routes.PROFILE) { ProfilePlaceholder() }
        }
    }
}
