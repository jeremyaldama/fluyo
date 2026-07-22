package com.qolve.fluyo.presentation.screens.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.presentation.screens.home.components.DayExpensesCard
import com.qolve.fluyo.presentation.screens.home.components.DayHeader
import java.time.LocalDate

/** Full expense history, grouped by day — the destination of Home's "Ver todo →". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllExpensesScreen(
    onBack: () -> Unit,
    onExpenseClick: (String) -> Unit,
    viewModel: AllExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val grouped: List<Pair<LocalDate, List<Expense>>> = remember(state.expenses) {
        state.expenses.groupBy { it.expenseDate }
            .toList()
            .sortedByDescending { it.first }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.all_expenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && !state.hasLoaded -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            errorMessage != null && !state.hasLoaded -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = viewModel::refresh,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            grouped.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(
                            onClick = viewModel::refresh,
                            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp),
                        ) { Text(stringResource(R.string.action_retry)) }
                    }
                    Text(
                        text = stringResource(R.string.all_expenses_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    errorMessage?.let { message ->
                        item(key = "load-error") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Button(
                                    onClick = viewModel::refresh,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                    grouped.forEach { (date, expenses) ->
                        item(key = "header-$date") { DayHeader(date = date) }
                        item(key = "card-$date") {
                            DayExpensesCard(
                                expenses = expenses,
                                categoriesById = state.categoriesById,
                                onExpenseClick = { onExpenseClick(it.id) },
                            )
                        }
                    }
                    item {
                        Text(
                            text = pluralStringResource(
                                R.plurals.all_expenses_count,
                                state.expenses.size,
                                state.expenses.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
