package com.qolve.fluyo.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.presentation.components.IllustratedEmptyState
import com.qolve.fluyo.presentation.screens.home.components.ExpenseRow
import com.qolve.fluyo.presentation.screens.home.components.HeroHomeCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val grouped: List<Pair<LocalDate, List<Expense>>> = remember(state.recentExpenses) {
        state.recentExpenses.groupBy { it.expenseDate }
            .toList()
            .sortedByDescending { it.first }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeroHomeCard(
                displayName = state.displayName,
                breakdown = state.breakdown,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_recent_expenses),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.recentExpenses.isEmpty()) {
            item {
                IllustratedEmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            grouped.forEach { (date, expenses) ->
                item(key = "header-$date") { DayHeader(date = date) }
                item(key = "card-$date") {
                    DayExpensesCard(
                        expenses = expenses,
                        categoriesById = state.categoriesById,
                    )
                }
            }
        }
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
            .padding(horizontal = 4.dp, vertical = 0.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun DayExpensesCard(
    expenses: List<Expense>,
    categoriesById: Map<String, Category>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            expenses.forEachIndexed { index, expense ->
                ExpenseRow(
                    expense = expense,
                    category = expense.categoryId?.let { categoriesById[it] },
                )
                if (index != expenses.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

private val dayHeaderFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"))
