package com.qolve.fluyo.presentation.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.outlined.Flag
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.screens.goals.components.CompletedGoalRow
import com.qolve.fluyo.presentation.screens.goals.components.ConfettiOverlay
import com.qolve.fluyo.presentation.screens.goals.components.DepositSheet
import com.qolve.fluyo.presentation.screens.goals.components.GoalCard

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
                onGoalClick = viewModel::openDepositSheet,
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
    onGoalClick: (com.qolve.fluyo.domain.model.Goal) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.goals_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(R.string.goals_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.active.isEmpty()) {
            item { GoalsEmptyState() }
        } else {
            items(state.active, key = { it.id }) { goal ->
                GoalCard(goal = goal, onClick = { onGoalClick(goal) })
            }
        }

        if (state.completed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
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

@Composable
private fun GoalsEmptyState() {
    IllustratedEmptyState(
        icon = Icons.Outlined.Flag,
        title = stringResource(R.string.goals_empty_title),
        subtitle = stringResource(R.string.goals_empty_subtitle),
        accent = MaterialTheme.colorScheme.tertiary,
    )
}
