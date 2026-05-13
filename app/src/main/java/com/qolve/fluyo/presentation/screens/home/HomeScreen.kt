package com.qolve.fluyo.presentation.screens.home

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.presentation.screens.home.components.BudgetCircle
import com.qolve.fluyo.presentation.screens.home.components.ExpenseRow
import com.qolve.fluyo.presentation.util.formatPen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val grouped = remember(state.recentExpenses) {
        state.recentExpenses.groupBy { it.expenseDate }
            .toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { GreetingHeader(displayName = state.displayName) }

        item { Spacer(Modifier.height(4.dp)) }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BudgetCircle(breakdown = state.breakdown)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.home_spent_so_far,
                        formatPen(state.breakdown.totalSpent),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_recent_expenses),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        if (state.recentExpenses.isEmpty()) {
            item { EmptyExpenses() }
        } else {
            grouped.forEach { (date, expenses) ->
                item(key = "header-$date") {
                    DayHeader(date = date)
                }
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        category = expense.categoryId?.let { state.categoriesById[it] },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingHeader(displayName: String?) {
    val greeting = displayName?.let { stringResource(R.string.home_greeting_named, it) }
        ?: stringResource(R.string.home_greeting_generic)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = stringResource(R.string.home_greeting_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val label = when (date) {
        LocalDate.now() -> stringResource(R.string.day_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.format(dayHeaderFmt)
    }
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun EmptyExpenses() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val dayHeaderFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"))

@Suppress("unused")
private fun Category.touch() = id

@Suppress("unused")
private fun Expense.touch() = id
