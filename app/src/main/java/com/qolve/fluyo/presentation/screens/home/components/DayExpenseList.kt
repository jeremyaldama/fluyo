package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Day-grouped expense list pieces shared by Home ("Movimientos") and the full
 * history screen. [DayHeader] renders the small-caps date eyebrow; [DayExpensesCard]
 * renders one card per day with a divider-separated [ExpenseRow] per expense.
 */
@Composable
fun DayHeader(date: LocalDate) {
    val label = when (date) {
        LocalDate.now() -> stringResource(R.string.home_day_today_caps)
        LocalDate.now().minusDays(1) -> stringResource(R.string.home_day_yesterday_caps)
        else -> date.format(dayHeaderFmt).uppercase(Locale.forLanguageTag("es-PE"))
    }
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.0.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
fun DayExpensesCard(
    expenses: List<Expense>,
    categoriesById: Map<String, Category>,
    onExpenseClick: ((Expense) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            expenses.forEachIndexed { index, expense ->
                ExpenseRow(
                    expense = expense,
                    category = expense.categoryId?.let { categoriesById[it] },
                    onClick = onExpenseClick?.let { handler -> { handler(expense) } },
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
