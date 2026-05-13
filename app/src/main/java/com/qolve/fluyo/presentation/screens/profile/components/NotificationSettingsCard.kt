package com.qolve.fluyo.presentation.screens.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.NudgeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsCard(
    enabled: Boolean,
    hour: Int,
    activeTypes: Set<NudgeType>,
    onToggleEnabled: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onToggleType: (NudgeType, Boolean) -> Unit,
    onSendTestNudge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_notifications_header),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = stringResource(R.string.profile_notifications_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Hour row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_notifications_hour),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.profile_notifications_hour_format, hour),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { pickerOpen = true }, enabled = enabled) {
                    Text(stringResource(R.string.profile_notifications_change))
                }
            }

            // Types
            Text(
                text = stringResource(R.string.profile_notifications_types),
                style = MaterialTheme.typography.titleSmall,
            )
            NudgeType.entries.forEach { type ->
                val isOn = type in activeTypes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(type.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(type.descriptionRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = isOn,
                        onCheckedChange = { onToggleType(type, it) },
                        enabled = enabled,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            TextButton(
                onClick = onSendTestNudge,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.profile_notifications_test))
            }
        }
    }

    if (pickerOpen) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(stringResource(R.string.profile_notifications_hour_dialog_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = pickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onHourChange(pickerState.hour)
                    pickerOpen = false
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }
}

private fun NudgeType.labelRes() = when (this) {
    NudgeType.PROGRESS -> R.string.nudge_type_progress
    NudgeType.REMINDER -> R.string.nudge_type_reminder
    NudgeType.BUDGET -> R.string.nudge_type_budget
    NudgeType.GOAL -> R.string.nudge_type_goal
}

private fun NudgeType.descriptionRes() = when (this) {
    NudgeType.PROGRESS -> R.string.nudge_type_progress_desc
    NudgeType.REMINDER -> R.string.nudge_type_reminder_desc
    NudgeType.BUDGET -> R.string.nudge_type_budget_desc
    NudgeType.GOAL -> R.string.nudge_type_goal_desc
}
