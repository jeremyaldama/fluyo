package com.qolve.fluyo.presentation.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.time.FluyoTime
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.presentation.util.datePickerUtcMillisToLocalDate
import com.qolve.fluyo.presentation.util.LocalDateSelectableDates
import com.qolve.fluyo.presentation.util.toDatePickerUtcMillis
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreateGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedOk) { if (state.savedOk) onSaved() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.create_goal_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.create_goal_name_label)) },
                placeholder = { Text(stringResource(R.string.create_goal_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.create_goal_target_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currencySymbol(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.targetInput,
                    onValueChange = viewModel::onTargetChange,
                    placeholder = {
                        Text(
                            text = "0.00",
                            style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Light),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.create_goal_deadline_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = viewModel::openDatePicker,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.deadline?.format(deadlineFmt)
                        ?: stringResource(R.string.create_goal_deadline_optional),
                )
            }

            Box(modifier = Modifier.weight(1f))

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.create_goal_save))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.showDatePicker) {
            val today = FluyoTime.today()
            val initialMillis = (state.deadline ?: today).toDatePickerUtcMillis()
            val selectableDates = remember(today) {
                LocalDateSelectableDates(today, LocalDate.of(2100, 12, 31))
            }
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialMillis,
                selectableDates = selectableDates,
            )
            DatePickerDialog(
                onDismissRequest = viewModel::closeDatePicker,
                confirmButton = {
                    TextButton(onClick = {
                        val ms = pickerState.selectedDateMillis
                        val picked = ms?.let(::datePickerUtcMillisToLocalDate)
                        viewModel.onDeadlinePicked(picked)
                    }) {
                        Text(stringResource(R.string.action_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::closeDatePicker) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

private val deadlineFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale.forLanguageTag("es-PE"))
