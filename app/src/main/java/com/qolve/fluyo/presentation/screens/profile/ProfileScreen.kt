package com.qolve.fluyo.presentation.screens.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.presentation.screens.profile.components.BadgeTile
import com.qolve.fluyo.presentation.screens.profile.components.LevelCard
import com.qolve.fluyo.presentation.screens.profile.components.NotificationSettingsCard
import com.qolve.fluyo.presentation.util.formatPen

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                strokeWidth = 3.dp,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(2) }) { ProfileHeader(displayName = state.user?.displayName) }
                item(span = { GridItemSpan(2) }) {
                    LevelCard(level = state.currentLevel, totalPoints = state.totalPoints)
                }
                item(span = { GridItemSpan(2) }) {
                    BudgetRow(
                        currentBudget = state.user?.monthlyBudget ?: 0.0,
                        onEdit = viewModel::openBudgetDialog,
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    NotificationSettingsCard(
                        enabled = state.user?.notificationEnabled ?: true,
                        hour = state.user?.notificationHour ?: 20,
                        activeTypes = state.user?.notificationTypes ?: NudgeType.entries.toSet(),
                        onToggleEnabled = viewModel::toggleNotificationsEnabled,
                        onHourChange = viewModel::setNotificationHour,
                        onToggleType = viewModel::toggleNotificationType,
                        onSendTestNudge = viewModel::fireTestNudge,
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = stringResource(R.string.profile_badges_header),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(BadgeType.entries.toList(), key = { it.wire }) { type ->
                    BadgeTile(type = type, unlocked = type in state.unlockedTypes)
                }
                item(span = { GridItemSpan(2) }) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = viewModel::signOut,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_sign_out))
                    }
                }
            }
        }
    }

    if (state.showBudgetDialog) {
        BudgetEditDialog(
            input = state.budgetInput,
            isSaving = state.isSavingBudget,
            error = state.errorMessage,
            onInputChange = viewModel::onBudgetInputChange,
            onDismiss = viewModel::closeBudgetDialog,
            onConfirm = viewModel::saveBudget,
        )
    }
}

@Suppress("FunctionName")
private fun GridItemSpan(span: Int) = androidx.compose.foundation.lazy.grid.GridItemSpan(span)

@Composable
private fun ProfileHeader(displayName: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: stringResource(R.string.profile_default_name),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.profile_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BudgetRow(currentBudget: Double, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_budget_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatPen(currentBudget),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    input: String,
    isSaving: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_budget_dialog_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.onboarding_currency_prefix),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving && input.toDoubleOrNull() != null) {
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
