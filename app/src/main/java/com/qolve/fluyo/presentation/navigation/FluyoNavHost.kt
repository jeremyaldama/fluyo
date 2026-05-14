package com.qolve.fluyo.presentation.navigation

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.navigation.navArgument
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.presentation.events.AppEvent
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.presentation.util.nameRes
import com.qolve.fluyo.presentation.screens.auth.LoginScreen
import com.qolve.fluyo.presentation.screens.expense.ManualEntryScreen
import com.qolve.fluyo.presentation.screens.goals.CreateGoalScreen
import com.qolve.fluyo.presentation.screens.goals.GoalsScreen
import com.qolve.fluyo.presentation.screens.home.HomeScreen
import com.qolve.fluyo.presentation.screens.home.components.AddExpenseSheet
import com.qolve.fluyo.presentation.screens.onboarding.OnboardingHost
import com.qolve.fluyo.presentation.screens.profile.ProfileScreen
import com.qolve.fluyo.presentation.screens.scan.OcrConfirmScreen
import com.qolve.fluyo.presentation.screens.stats.StatsScreen

@Composable
fun FluyoNavHost(
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val state by rootViewModel.uiState.collectAsStateWithLifecycle()
    val rootNav = rememberNavController()

    // Single effect: navigate to the auth-gated start destination FIRST, then
    // (only on MAIN) watch for shared images. Splitting these into two
    // LaunchedEffects races: the popUpTo(0) below would wipe any scanConfirm
    // that a parallel share-target effect pushed onto the back stack.
    LaunchedEffect(state.startRoute) {
        val route = state.startRoute ?: return@LaunchedEffect
        rootNav.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        if (route != Routes.MAIN) return@LaunchedEffect

        rootViewModel.sharedImageEvents.events.collect { uri ->
            val encoded = Uri.encode(uri.toString())
            rootNav.navigate(Routes.scanConfirm(encoded)) {
                launchSingleTop = true
            }
            rootViewModel.sharedImageEvents.consume()
        }
    }

    NavHost(
        navController = rootNav,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) { SplashRoute() }
        composable(Routes.LOGIN) {
            LoginScreen(
                composeAuth = rootViewModel.composeAuth,
                onUseEmailPassword = { rootNav.navigate(Routes.EMAIL_AUTH) },
            )
        }
        composable(Routes.EMAIL_AUTH) {
            com.qolve.fluyo.presentation.screens.auth.EmailAuthScreen(
                onBack = { rootNav.popBackStack() },
                // Auth state flow will pick the new session up and redirect via startRoute;
                // pop here so the back-stack doesn't keep the auth screen behind MAIN.
                onSignedIn = { rootNav.popBackStack(Routes.LOGIN, inclusive = true) },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingHost(onFinished = { rootViewModel.markOnboardingDone() })
        }
        composable(Routes.MAIN) {
            MainShell(
                appEvents = rootViewModel.appEvents,
                onOpenManualEntry = { rootNav.navigate(Routes.MANUAL_ENTRY) },
                onOpenScan = { uri ->
                    val encoded = Uri.encode(uri.toString())
                    rootNav.navigate(Routes.scanConfirm(encoded))
                },
                onOpenGoalCreate = { rootNav.navigate(Routes.GOAL_CREATE) },
            )
        }
        composable(Routes.MANUAL_ENTRY) {
            ManualEntryScreen(
                onClose = { rootNav.popBackStack() },
                onSaved = { rootNav.popBackStack() },
            )
        }
        composable(
            route = Routes.SCAN_CONFIRM_ROUTE,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) {
            OcrConfirmScreen(
                onClose = { rootNav.popBackStack() },
                onSaved = { rootNav.popBackStack(Routes.MAIN, inclusive = false) },
            )
        }
        composable(Routes.GOAL_CREATE) {
            CreateGoalScreen(
                onClose = { rootNav.popBackStack() },
                onSaved = { rootNav.popBackStack() },
            )
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
private fun MainShell(
    appEvents: AppEvents,
    onOpenManualEntry: () -> Unit,
    onOpenScan: (Uri) -> Unit,
    onOpenGoalCreate: () -> Unit,
) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Routes.HOME

    var sheetOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    val savedMessage = stringResource(R.string.expense_saved_snackbar)
    val badgePrefix = stringResource(R.string.badge_unlocked_prefix)

    LaunchedEffect(Unit) {
        appEvents.events.collect { event ->
            when (event) {
                is AppEvent.ExpenseSaved -> {
                    snackbarHostState.showSnackbar(savedMessage)
                }
                is AppEvent.BadgeUnlocked -> {
                    val type = BadgeType.entries.firstOrNull { it.wire == event.typeWire }
                    if (type != null) {
                        val name = ctx.getString(type.nameRes())
                        snackbarHostState.showSnackbar("$badgePrefix $name")
                    }
                }
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { onOpenScan(it) }
    }

    // Ask once for POST_NOTIFICATIONS on Android 13+. The system enforces
    // "don't ask again" on its own after the user denies twice, so we don't
    // need extra bookkeeping.
    val notificationsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* result ignored — worker re-checks at fire time */ }
    } else null
    LaunchedEffect(Unit) {
        notificationsPermission?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        bottomBar = { BottomNavBar(nav) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentRoute == Routes.HOME) {
                // Wrap the FAB in a Box so we can paint the coral brand-dot motif at the
                // bottom-right corner, matching the home mockup. The dot is purely
                // decorative — it doesn't intercept clicks (the FAB owns the full hit area).
                androidx.compose.foundation.layout.Box {
                    FloatingActionButton(
                        onClick = { sheetOpen = true },
                        containerColor = com.qolve.fluyo.presentation.theme.TealRamp500,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                    }
                    com.qolve.fluyo.presentation.theme.BrandDot(
                        modifier = androidx.compose.ui.Modifier
                            .align(androidx.compose.ui.Alignment.BottomEnd)
                            .padding(2.dp),
                        size = 8.dp,
                        color = com.qolve.fluyo.presentation.theme.CoralRamp500,
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.GOALS) { GoalsScreen(onCreateGoal = onOpenGoalCreate) }
            composable(Routes.PROFILE) { ProfileScreen() }
        }
    }

    if (sheetOpen) {
        AddExpenseSheet(
            onDismiss = { sheetOpen = false },
            onManual = {
                sheetOpen = false
                onOpenManualEntry()
            },
            onScan = {
                sheetOpen = false
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onVoice = { sheetOpen = false },
        )
    }
}
