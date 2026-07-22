package com.qolve.fluyo.presentation.navigation

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.qolve.fluyo.data.voice.VoiceParser
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType
import androidx.compose.runtime.CompositionLocalProvider
import com.qolve.fluyo.presentation.events.AppEvent
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.presentation.util.LocalCurrencySymbol
import com.qolve.fluyo.presentation.util.currencySymbolFor
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
import kotlinx.coroutines.launch

@Composable
fun FluyoNavHost(
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val state by rootViewModel.uiState.collectAsStateWithLifecycle()
    val currencyCode by rootViewModel.currencyCode.collectAsStateWithLifecycle()
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

    CompositionLocalProvider(LocalCurrencySymbol provides currencySymbolFor(currencyCode)) {
    NavHost(
        navController = rootNav,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashRoute(
                error = state.sessionError,
                onRetry = rootViewModel::retryProvisioning,
            )
        }
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
                onOpenManageCategories = { rootNav.navigate(Routes.MANAGE_CATEGORIES) },
                onOpenVoiceEntry = { amount, desc ->
                    rootNav.navigate(Routes.manualEntryPrefilled(amount, desc, "voice"))
                },
                onOpenExpense = { id -> rootNav.navigate(Routes.editExpense(id)) },
                onOpenAllExpenses = { rootNav.navigate(Routes.ALL_EXPENSES) },
            )
        }
        composable(
            route = Routes.MANUAL_ENTRY_ROUTE,
            arguments = listOf(
                navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                navArgument("desc") { type = NavType.StringType; defaultValue = "" },
                navArgument("src") { type = NavType.StringType; defaultValue = "manual" },
                navArgument("expenseId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ManualEntryScreen(
                onClose = { rootNav.popBackStack() },
                onSaved = { rootNav.popBackStack() },
            )
        }
        composable(Routes.ALL_EXPENSES) {
            com.qolve.fluyo.presentation.screens.expense.AllExpensesScreen(
                onBack = { rootNav.popBackStack() },
                onExpenseClick = { id -> rootNav.navigate(Routes.editExpense(id)) },
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
        composable(Routes.MANAGE_CATEGORIES) {
            com.qolve.fluyo.presentation.screens.profile.ManageCategoriesScreen(
                onBack = { rootNav.popBackStack() },
            )
        }
    }
    }
}

@Composable
private fun SplashRoute(error: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (error == null) {
            CircularProgressIndicator()
        } else {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_provisioning_error),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
@SuppressLint("InlinedApi") // POST_NOTIFICATIONS launcher exists only inside the API 33 guard.
private fun MainShell(
    appEvents: AppEvents,
    onOpenManualEntry: () -> Unit,
    onOpenScan: (Uri) -> Unit,
    onOpenGoalCreate: () -> Unit,
    onOpenManageCategories: () -> Unit,
    onOpenVoiceEntry: (amount: String, desc: String) -> Unit,
    onOpenExpense: (String) -> Unit,
    onOpenAllExpenses: () -> Unit,
) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Routes.HOME

    var sheetOpen by remember { mutableStateOf(false) }
    var showVoiceDisclosure by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val mainShellScope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.expense_saved_snackbar)
    val badgePrefix = stringResource(R.string.badge_unlocked_prefix)
    val voicePrompt = stringResource(R.string.add_option_voice_hint)
    val voiceUnavailable = stringResource(R.string.voice_recognizer_unavailable)
    val addExpenseDescription = stringResource(R.string.add_sheet_title)
    // Precomputed badge names (stringResource can't be called from the event collector).
    val badgeNames = BadgeType.entries.associateWith { stringResource(it.nameRes()) }

    LaunchedEffect(Unit) {
        appEvents.events.collect { event ->
            when (event) {
                is AppEvent.ExpenseSaved -> {
                    snackbarHostState.showSnackbar(savedMessage)
                }
                is AppEvent.BadgeUnlocked -> {
                    val type = BadgeType.entries.firstOrNull { it.wire == event.typeWire }
                    if (type != null) {
                        val name = badgeNames[type].orEmpty()
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

    // In-app camera capture for physical receipts. TakePicture writes to a FileProvider
    // URI in cacheDir/captures (no CAMERA permission needed — the system camera handles
    // capture). The pending URI survives process death via rememberSaveable.
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val captured = pendingCaptureUri?.let(Uri::parse)
        if (success) {
            captured?.let(onOpenScan)
        } else {
            captured?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        }
        pendingCaptureUri = null
    }
    val launchCamera: () -> Unit = {
        var createdUri: Uri? = null
        runCatching {
            val dir = java.io.File(context.cacheDir, "captures").apply { mkdirs() }
            val file = java.io.File.createTempFile("receipt-", ".jpg", dir)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            createdUri = uri
            pendingCaptureUri = uri.toString()
            takePicture.launch(uri)
        }.onFailure {
            createdUri?.let { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            pendingCaptureUri = null
        }
    }

    // In-app voice entry (HU-05). RecognizerIntent shows the system speech UI (on-device
    // where available) and returns a transcript — no RECORD_AUDIO permission needed. We
    // parse it and hand off to the manual-entry screen, pre-filled, for confirmation.
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            val parsed = VoiceParser.parse(transcript)
            val amount = parsed.amount?.let {
                it.toBigDecimal().stripTrailingZeros().toPlainString()
            }.orEmpty()
            onOpenVoiceEntry(amount, parsed.description.orEmpty())
        }
    }
    val launchSpeech: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure {
                mainShellScope.launch { snackbarHostState.showSnackbar(voiceUnavailable) }
            }
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
                        modifier = Modifier.semantics {
                            contentDescription = addExpenseDescription
                        },
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
            composable(Routes.HOME) {
                HomeScreen(
                    // Tapping the avatar in the home header should land on the Perfil tab.
                    // Use the same nav options the bottom-nav buttons use so the back stack
                    // doesn't accumulate one entry per tap.
                    onAvatarClick = {
                        nav.navigate(Routes.PROFILE) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onExpenseClick = onOpenExpense,
                    onViewAll = onOpenAllExpenses,
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.GOALS) { GoalsScreen(onCreateGoal = onOpenGoalCreate) }
            composable(Routes.PROFILE) {
                ProfileScreen(onManageCategories = onOpenManageCategories)
            }
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
            onCamera = {
                sheetOpen = false
                launchCamera()
            },
            onVoice = {
                sheetOpen = false
                showVoiceDisclosure = true
            },
        )
    }

    if (showVoiceDisclosure) {
        AlertDialog(
            onDismissRequest = { showVoiceDisclosure = false },
            title = { Text(stringResource(R.string.voice_disclosure_title)) },
            text = { Text(stringResource(R.string.voice_disclosure_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVoiceDisclosure = false
                        launchSpeech()
                    },
                ) {
                    Text(stringResource(R.string.voice_disclosure_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceDisclosure = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
