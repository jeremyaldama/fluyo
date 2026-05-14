package com.qolve.fluyo.presentation.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import coil.compose.AsyncImage
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.presentation.screens.profile.components.BadgeTile
import com.qolve.fluyo.presentation.screens.profile.components.NotificationSettingsCard
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealDark
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.util.descriptionRes
import com.qolve.fluyo.presentation.util.icon
import com.qolve.fluyo.presentation.util.formatPen
import com.qolve.fluyo.presentation.util.nameRes
import java.util.Locale

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
        return
    }

    val user = state.user
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ProfileHero(
                user = user,
                totalPoints = state.totalPoints,
                levelNumber = state.currentLevel.number,
                levelKey = state.currentLevel.key,
                progressToNext = state.currentLevel.progressTo(state.totalPoints),
                nextThreshold = state.currentLevel.nextThreshold,
            )
        }

        item {
            StatsStrip(
                points = state.totalPoints,
                unlocked = state.unlockedTypes.size,
                totalBadges = BadgeType.entries.size,
            )
        }

        // Insignias section: "next badge" callout + 2-col grid below.
        item {
            SectionTitle(stringResource(R.string.profile_badges_header))
        }
        item {
            NextBadgeCallout(unlocked = state.unlockedTypes)
        }
        // Render the badge grid manually as 2-up rows (we're inside a LazyColumn — nesting
        // a LazyVerticalGrid is allowed but uses fixed heights and complicates measurement).
        val rows = BadgeType.entries.chunked(2)
        items(rows.size, key = { idx -> "badge-row-$idx" }) { idx ->
            val row = rows[idx]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { type ->
                    Box(modifier = Modifier.weight(1f)) {
                        BadgeTile(type = type, unlocked = type in state.unlockedTypes)
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        item { SectionTitle(stringResource(R.string.profile_section_settings)) }
        item {
            SettingsGroup(
                user = user,
                onEditBudget = viewModel::openBudgetDialog,
                onToggleNotificationsEnabled = viewModel::toggleNotificationsEnabled,
                onHourChange = viewModel::setNotificationHour,
                onToggleNotificationType = viewModel::toggleNotificationType,
                onSendTestNudge = viewModel::fireTestNudge,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_sign_out),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
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

// ─────────────────────────────────────────────────────────────────────────────
// Hero: avatar + name + email + level chip + progress to next level
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHero(
    user: User?,
    totalPoints: Int,
    levelNumber: Int,
    levelKey: String,
    progressToNext: Float,
    nextThreshold: Int?,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressToNext,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "levelProgress",
    )
    val displayName = user?.displayName ?: stringResource(R.string.profile_default_name)
    val email = user?.email ?: stringResource(R.string.profile_email_fallback)
    val levelLabel = stringResource(R.string.profile_level_chip, levelNumber, localizedLevelName(levelKey))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(FluyoTealLight, FluyoTeal, FluyoTealDark),
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = displayName, photoUrl = user?.avatarUrl, size = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        // Level chip
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EmojiEvents,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = levelLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Progress to next level — chunky 8 dp white-on-translucent bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (nextThreshold == null) {
                        stringResource(R.string.profile_progress_caption_max)
                    } else {
                        stringResource(R.string.profile_progress_caption, totalPoints, nextThreshold)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun Avatar(name: String, photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
    val initials = remember(name) { initialsOf(name) }
    val initialsColor = remember(name) { initialsAvatarColor(name) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(initialsColor.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials,
                style = TextStyle(
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.ROOT)
        else -> (parts.first().firstOrNull()?.toString().orEmpty() +
            parts.last().firstOrNull()?.toString().orEmpty()).uppercase(Locale.ROOT)
    }
}

/** Deterministic palette pick from name. Same person always gets the same color. */
private fun initialsAvatarColor(name: String): Color {
    var h = 5381
    for (c in name) h = (h shl 5) + h + c.code
    val idx = ((h % avatarPalette.size) + avatarPalette.size) % avatarPalette.size
    return avatarPalette[idx]
}

private val avatarPalette = listOf(
    Color(0xFF00897B), Color(0xFFFF7043), Color(0xFF7E57C2),
    Color(0xFF42A5F5), Color(0xFFEC407A), Color(0xFFFFB300),
)

@Composable
private fun localizedLevelName(key: String): String = when (key) {
    "novato" -> stringResource(R.string.level_novato)
    "aprendiz" -> stringResource(R.string.level_aprendiz)
    "organizado" -> stringResource(R.string.level_organizado)
    "experto" -> stringResource(R.string.level_experto)
    else -> stringResource(R.string.level_maestro)
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats strip — 3 mini-cards under the hero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsStrip(points: Int, unlocked: Int, totalBadges: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCell(
            label = stringResource(R.string.profile_stat_points),
            value = points.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = stringResource(R.string.profile_stat_badges),
            value = stringResource(R.string.profile_stat_badges_value, unlocked, totalBadges),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = stringResource(R.string.profile_stat_streak),
            // Streak isn't computed in the current data layer — surface a placeholder until
            // a streak repository exists. Don't lie: show em-dash.
            value = stringResource(R.string.profile_stat_streak_empty),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Next-badge callout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NextBadgeCallout(unlocked: Set<BadgeType>) {
    val next = BadgeType.entries.firstOrNull { it !in unlocked }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = FluyoTeal.copy(alpha = 0.10f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FluyoTeal.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = (next ?: BadgeType.FIRST_EXPENSE).icon(),
                    contentDescription = null,
                    tint = FluyoTeal,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_next_badge_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                if (next != null) {
                    Text(
                        text = stringResource(next.nameRes()),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = stringResource(next.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.profile_next_badge_locked_all),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (next != null) {
                Text(
                    text = "+${next.points}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = FluyoTeal,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Configuración group — budget / phone / notifications expander
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    user: User?,
    onEditBudget: () -> Unit,
    onToggleNotificationsEnabled: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onToggleNotificationType: (NudgeType, Boolean) -> Unit,
    onSendTestNudge: () -> Unit,
) {
    var notificationsOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            SettingsRow(
                icon = Icons.Outlined.Savings,
                label = stringResource(R.string.profile_budget_label),
                value = formatPen(user?.monthlyBudget ?: 0.0),
                onClick = onEditBudget,
            )
            ThinDivider()
            SettingsRow(
                icon = Icons.Outlined.Phone,
                label = stringResource(R.string.profile_phone_label),
                value = user?.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.profile_phone_unset),
                // Phone editing isn't in the ViewModel yet — wired but no-op until a savePhone()
                // method exists. Click feedback still useful for discoverability.
                onClick = null,
            )
            ThinDivider()
            NotificationsRow(
                user = user,
                expanded = notificationsOpen,
                onToggleExpanded = { notificationsOpen = !notificationsOpen },
            )
            AnimatedVisibility(visible = notificationsOpen) {
                // Re-use the existing dense settings card inside the expander.
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    NotificationSettingsCard(
                        enabled = user?.notificationEnabled ?: true,
                        hour = user?.notificationHour ?: 20,
                        activeTypes = user?.notificationTypes ?: NudgeType.entries.toSet(),
                        onToggleEnabled = onToggleNotificationsEnabled,
                        onHourChange = onHourChange,
                        onToggleType = onToggleNotificationType,
                        onSendTestNudge = onSendTestNudge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)?,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationsRow(user: User?, expanded: Boolean, onToggleExpanded: () -> Unit) {
    val statusText = if (user?.notificationEnabled == false) {
        stringResource(R.string.profile_notifications_row_off)
    } else {
        stringResource(R.string.profile_notifications_row_on, user?.notificationHour ?: 20)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.profile_notifications_row_label),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = 4.dp),
    )
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
