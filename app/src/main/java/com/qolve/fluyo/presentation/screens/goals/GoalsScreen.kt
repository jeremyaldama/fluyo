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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.screens.goals.components.CompletedGoalRow
import com.qolve.fluyo.presentation.screens.goals.components.ConfettiOverlay
import com.qolve.fluyo.presentation.screens.goals.components.DepositSheet
import com.qolve.fluyo.presentation.screens.goals.components.GoalCard
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.util.formatPen

@Composable
fun GoalsScreen(
    onCreateGoal: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val goalCompletedText = stringResource(R.string.goal_completed_snackbar)

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
                    onClick = onCreateGoal,
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

        if (state.active.isNotEmpty()) {
            item {
                ActiveSummaryCard(active = state.active)
            }
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

        if (state.completed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.goals_completed_header),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            items(state.completed, key = { it.id }) { goal ->
                CompletedGoalRow(goal = goal)
            }
        }
    }
}

/**
 * Aggregate card across all active goals — gives the user a single-glance view of how
 * close they are to all of their targets combined. Particularly useful on a screen with
 * 3+ goals; with one goal it duplicates the goal card slightly, but the framing
 * ("Tienes 1 meta activa") still reinforces commitment.
 */
@Composable
private fun ActiveSummaryCard(active: List<Goal>) {
    val totalSaved = active.sumOf { it.currentAmount }
    val totalTarget = active.sumOf { it.targetAmount }
    val overallProgress = if (totalTarget > 0.0) (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
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
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.goals_summary_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (active.size == 1) {
                        stringResource(R.string.goals_summary_active_one)
                    } else {
                        stringResource(R.string.goals_summary_active_other, active.size)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.goals_summary_saved,
                        formatPen(totalSaved),
                        formatPen(totalTarget),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(12.dp))
                // Aggregate progress bar — thinner than the per-goal bar (6dp).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(overallProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White),
                    )
                }
            }
        }
    }
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
