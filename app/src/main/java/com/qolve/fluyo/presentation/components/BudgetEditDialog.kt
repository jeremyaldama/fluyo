package com.qolve.fluyo.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.presentation.util.money
import java.math.RoundingMode

/**
 * Base-budget editor, shared by Profile (Ajustes row) and Home (tapping the budget
 * ring). Stateless — each host ViewModel owns show/save state, per the house pattern.
 *
 * The dialog edits the BASE budget; one-off month extras are managed via [onAddExtra]
 * (→ [ExtraIncomeDialog]). When extras exist, a caption shows the effective total so
 * the base-vs-effective distinction stays visible.
 */
@Composable
fun BudgetEditDialog(
    input: String,
    isSaving: Boolean,
    error: String?,
    extraIncome: MoneyAmount,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onAddExtra: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_budget_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.budget_dialog_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currencySymbol(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        placeholder = { Text(stringResource(R.string.onboarding_budget_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (extraIncome > MoneyAmount.ZERO) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.budget_dialog_effective_caption,
                            money(
                                MoneyAmount.parse(input, RoundingMode.UNNECESSARY)
                                    ?: MoneyAmount.ZERO,
                            ),
                            money(extraIncome),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAddExtra, enabled = !isSaving) {
                    Text(stringResource(R.string.budget_dialog_add_extra))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving && MoneyAmount.parse(input, RoundingMode.UNNECESSARY) != null,
            ) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_back))
            }
        },
    )
}
