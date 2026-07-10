package com.qolve.fluyo.presentation.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.domain.usecase.CreateGoalUseCase.Companion.MAX_ACTIVE_GOALS
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.screens.goals.components.CompletedGoalRow
import com.qolve.fluyo.presentation.screens.goals.components.ConfettiOverlay
import com.qolve.fluyo.presentation.screens.goals.components.DepositSheet
import com.qolve.fluyo.presentation.screens.goals.components.GoalCard
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.util.currencySymbol

@Composable
fun GoalsScreen(
    onCreateGoal: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val goalCompletedText = stringResource(R.string.goal_completed_snackbar)
    val limitReachedText = stringResource(R.string.goals_limit_reached, MAX_ACTIVE_GOALS)

    // HU-07: at the cap, tapping "Nueva meta" surfaces a hint instead of navigating.
    val onCreateGoalGated: () -> Unit = {
        if (state.active.size >= MAX_ACTIVE_GOALS) {
            scope.launch { snackbarHostState.showSnackbar(limitReachedText) }
        } else {
            onCreateGoal()
        }
    }

    LaunchedEffect(state.showConfetti) {
        if (state.showConfetti) {
            snackbarHostState.showSnackbar(goalCompletedText)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onCreateGoalGated,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.goals_create_cta)) },
                )
            },
        ) { inner ->
            GoalsContent(
                state = state,
                onCreateGoal = onCreateGoal,
                onGoalDeposit = viewModel::openDepositSheet,
                contentPadding = inner,
            )
        }

        AnimatedVisibility(visible = state.showConfetti) {
            ConfettiOverlay(
                triggerKey = state.showConfetti,
                onFinished = { viewModel.consumeConfetti() },
            )
        }
    }

    state.depositSheetGoal?.let { goal ->
        DepositSheet(
            goal = goal,
            input = state.depositInput,
            isSaving = state.isDepositing,
            error = state.depositError,
            canSave = state.canDeposit,
            onInputChange = viewModel::onDepositChange,
            onDismiss = viewModel::closeDepositSheet,
            onConfirm = viewModel::deposit,
            onDelete = viewModel::deleteGoal,
        )
    }
}

@Composable
private fun GoalsContent(
    state: GoalsUiState,
    onCreateGoal: () -> Unit,
    onGoalDeposit: (Goal) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.goals_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.goals_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Hero card shows whenever there's any goal at all (active OR completed)
        if (state.active.isNotEmpty() || state.completed.isNotEmpty()) {
            item {
                GoalsHeroCard(active = state.active, completed = state.completed)
            }
        }

        if (state.active.isNotEmpty()) {
            items(state.active, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onClick = { onGoalDeposit(goal) },
                    onDeposit = { onGoalDeposit(goal) },
                )
            }
        } else if (state.completed.isEmpty()) {
            // Truly empty — no active and no completed.
            item { GoalsEmptyState(onCreateGoal = onCreateGoal) }
        }

        // Completed goals show inline with active ones using the same GoalCard
        // (the card renders its completed state internally — coral border + "¡Logrado!").
        if (state.completed.isNotEmpty()) {
            items(state.completed, key = { "done-${it.id}" }) { goal ->
                GoalCard(
                    goal = goal,
                    onClick = { onGoalDeposit(goal) },
                    onDeposit = { onGoalDeposit(goal) },
                )
            }
        }
    }
}

/**
 * Hero card on the Metas screen. Teal gradient surface, "AHORRADO EN TOTAL" eyebrow,
 * huge total (saved across all active + completed goals), and a one-liner with the active
 * count + completion count. A big faded F-mark sits in the bottom-right as a brand watermark.
 */
@Composable
private fun GoalsHeroCard(active: List<Goal>, completed: List<Goal>) {
    val totalSaved = (active + completed).sumOf { it.currentAmount }
    val integer = totalSaved.toLong()
    val integerFormatted = java.text.NumberFormat.getNumberInstance(
        java.util.Locale.forLanguageTag("es-PE"),
    ).format(integer)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(FluyoTealLight, FluyoTeal),
                ),
            ),
    ) {
        // Faded F. watermark in the bottom-right corner — large, low-opacity brand mark.
        Text(
            text = "F.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 120.sp,
                color = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.goals_hero_label),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = Color.White.copy(alpha = 0.80f),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = currencySymbol(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    ),
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = integerFormatted,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                    ),
                    color = Color.White,
                )
                Text(
                    text = ".00",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    ),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = heroSubtitle(active.size, completed.size),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun heroSubtitle(activeCount: Int, completedCount: Int): String = when {
    activeCount == 0 && completedCount == 0 -> stringResource(R.string.goals_summary_active_one).let { "" }
    activeCount == 0 && completedCount == 1 -> stringResource(R.string.goals_hero_no_active_completed_one)
    activeCount == 0 -> stringResource(R.string.goals_hero_no_active_completed_other, completedCount)
    completedCount == 0 && activeCount == 1 -> stringResource(R.string.goals_hero_active_only_one)
    completedCount == 0 -> stringResource(R.string.goals_hero_active_only_other, activeCount)
    activeCount == 1 && completedCount == 1 -> stringResource(R.string.goals_hero_active_completed_one_one)
    else -> stringResource(R.string.goals_hero_active_completed_other, activeCount, completedCount)
}

@Composable
private fun GoalsEmptyState(onCreateGoal: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IllustratedEmptyState(
            icon = Icons.Outlined.Flag,
            title = stringResource(R.string.goals_empty_title),
            subtitle = stringResource(R.string.goals_empty_subtitle),
            accent = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(12.dp))
        // Inline primary CTA — duplicates the FAB, but the FAB is easy to miss on first
        // empty-state load. A button right under the explainer text converts much better.
        androidx.compose.material3.Button(
            onClick = onCreateGoal,
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.padding(end = 6.dp))
            Text(stringResource(R.string.goals_create_cta))
        }
    }
}
