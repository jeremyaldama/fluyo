package com.qolve.fluyo.presentation.screens.goals.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.presentation.util.formatPen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSheet(
    goal: Goal,
    input: String,
    isSaving: Boolean,
    error: String?,
    canSave: Boolean,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.deposit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = goal.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.onboarding_currency_prefix),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "0.00",
                            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.deposit_remaining, formatPen(goal.remaining)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.deposit_confirm))
                }
            }
        }
    }
}
