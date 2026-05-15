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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.presentation.screens.profile.components.NotificationSettingsCard
import com.qolve.fluyo.presentation.theme.CoralRamp500
// Direct NeutralRamp references removed — they don't flip on dark mode. Text + chrome
// now reads from MaterialTheme.colorScheme.* which the FluyoTheme wires per system mode.
import com.qolve.fluyo.presentation.theme.TealRamp100
import com.qolve.fluyo.presentation.theme.TealRamp500
import com.qolve.fluyo.presentation.util.emoji
import com.qolve.fluyo.presentation.util.formatPen
import com.qolve.fluyo.presentation.util.levelNameRes
import com.qolve.fluyo.presentation.util.nameRes
import com.qolve.fluyo.presentation.util.openFluyoOnWhatsApp
import androidx.compose.ui.platform.LocalContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Perfil screen — redesigned per mockup 08.
 *
 * Sections, top-down:
 *   1. Hero — circular avatar with optional coral notif badge, name, "Lima · desde …" subtitle.
 *   2. Level card — NIVEL eyebrow + level name + XP + chunky progress bar + 🔥 streak chip.
 *   3. Medallas grid — 4×2 emoji tiles, coral dot on unlocked tiles, count "4 / 8" header.
 *   4. Ajustes — settings rows (budget, phone, notifications expander) + destructive sign-out.
 */
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ProfileHero(user = user) }

        item {
            LevelCard(
                levelNumber = state.currentLevel.number,
                levelKey = state.currentLevel.key,
                totalPoints = state.totalPoints,
                nextThreshold = state.currentLevel.nextThreshold,
                progressToNext = state.currentLevel.progressTo(state.totalPoints),
                streak = state.streak,
            )
        }

        item {
            MedallasSection(unlocked = state.unlockedTypes)
        }

        item {
            SectionTitle(stringResource(R.string.profile_section_settings_simple))
        }

        item {
            AjustesCard(
                user = user,
                onEditBudget = viewModel::openBudgetDialog,
                onEditPhone = viewModel::openPhoneDialog,
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
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
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

    if (state.showPhoneDialog) {
        PhoneEditDialog(
            input = state.phoneInput,
            isSaving = state.isSavingPhone,
            error = state.errorMessage,
            onInputChange = viewModel::onPhoneInputChange,
            onDismiss = viewModel::closePhoneDialog,
            onConfirm = viewModel::savePhone,
        )
    }
}

// ─── Hero ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHero(user: User?) {
    val displayName = user?.displayName ?: stringResource(R.string.profile_default_name)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarWithBadge(displayName = displayName, photoUrl = user?.avatarUrl, notifCount = 0)
        Spacer(Modifier.height(14.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = profileSubtitle(user),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun profileSubtitle(user: User?): String {
    val since = user?.memberSince
    if (since == null) return user?.email ?: ""
    val local = since.atZone(ZoneId.systemDefault())
    val fmt = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-PE")) }
    val asString = local.format(fmt).replaceFirstChar { it.lowercase() }
    // Two variants: with phone (we know the user opted into WhatsApp, treat as Peru-based),
    // or just the member-since date. Avoid hardcoding a city we don't have data for.
    return if (!user.phoneNumber.isNullOrBlank()) {
        stringResource(R.string.profile_member_since_location, "Lima", asString)
    } else {
        stringResource(R.string.profile_member_since, asString)
    }
}

@Composable
private fun AvatarWithBadge(displayName: String, photoUrl: String?, notifCount: Int) {
    val initials = remember(displayName) { initialsOf(displayName) }
    Box {
        // Outer halo + inner circle. Use the brand's primary-container so both light + dark
        // get a softly tinted ring (mint in light, deep teal in dark) instead of a raw
        // hex that turns muddy when inverted.
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = initials,
                        style = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealRamp500,
                        ),
                    )
                }
            }
        }
        // Notif badge — coral disc with the count. Hidden when count == 0 so we don't
        // promise unread items we can't actually surface yet.
        if (notifCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CoralRamp500),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = notifCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        else -> parts.first().firstOrNull()?.toString()?.uppercase(Locale.ROOT) ?: "?"
    }
}

// ─── Level card ─────────────────────────────────────────────────────────────

@Composable
private fun LevelCard(
    levelNumber: Int,
    levelKey: String,
    totalPoints: Int,
    nextThreshold: Int?,
    progressToNext: Float,
    streak: Int,
) {
    val animated by animateFloatAsState(
        targetValue = progressToNext,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "levelProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
    ) {
        Column {
            // Title row — eyebrow + name + XP value
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_level_eyebrow, levelNumber),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(levelNameRes(levelKey)),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = totalPoints.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.profile_level_xp_suffix),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Progress bar — chunky 8dp. Brand color from the theme so it stays vivid on
            // both light and dark; the track uses an alpha-blended version so it sits
            // visibly above any surface.
            val barColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Bottom row — XP to next level on the left, streak chip on the right
            Row(verticalAlignment = Alignment.CenterVertically) {
                val toNextText = if (nextThreshold == null) {
                    stringResource(R.string.profile_level_max_caption)
                } else {
                    val xpRemaining = (nextThreshold - totalPoints).coerceAtLeast(0)
                    stringResource(R.string.profile_level_to_next, xpRemaining, levelNumber + 1)
                }
                Text(
                    text = toNextText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (streak > 0) {
                        stringResource(R.string.profile_level_streak, streak)
                    } else {
                        stringResource(R.string.profile_level_streak_none)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ─── Medallas grid ──────────────────────────────────────────────────────────

@Composable
private fun MedallasSection(unlocked: Set<BadgeType>) {
    val all = BadgeType.entries.toList()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.profile_medallas_header),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.profile_medallas_count, unlocked.size, all.size),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(12.dp))
        // 4-column rows — fits 8 badges in 2 rows. Stable layout regardless of unlock count.
        all.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { type ->
                    Box(modifier = Modifier.weight(1f)) {
                        BadgeTile(type = type, unlocked = type in unlocked)
                    }
                }
                // Pad the last row if it doesn't fill 4 slots
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(type: BadgeType, unlocked: Boolean) {
    // surfaceVariant is the "recessed" surface in both modes — light grey in light, dark
    // grey-with-a-hint-of-teal in dark. Both unlocked + locked sit on it so locked tiles
    // still read as cards (not as the canvas) on a dark phone.
    val containerColor = if (unlocked) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            // Subtler dim on dark — 0.55 swallowed the emoji + text completely.
            .alpha(if (unlocked) 1f else 0.7f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = type.emoji(),
                style = TextStyle(fontSize = 32.sp),
            )
            Text(
                text = stringResource(type.nameRes()),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                ),
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        // Coral dot in the top-right of unlocked tiles — the recurring brand motif.
        if (unlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(CoralRamp500),
            )
        }
    }
}

// ─── Ajustes card ───────────────────────────────────────────────────────────

@Composable
private fun AjustesCard(
    user: User?,
    onEditBudget: () -> Unit,
    onEditPhone: () -> Unit,
    onToggleNotificationsEnabled: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onToggleNotificationType: (NudgeType, Boolean) -> Unit,
    onSendTestNudge: () -> Unit,
) {
    var notificationsOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column {
            // Budget row — always wired
            SettingsRow(
                icon = Icons.Outlined.Savings,
                title = stringResource(R.string.profile_budget_label),
                subtitle = stringResource(R.string.profile_budget_row_subtitle),
                value = formatPen(user?.monthlyBudget ?: 0.0),
                onClick = onEditBudget,
            )
            ThinDivider()
            // Phone — opens the edit dialog. Clearing the field saves null so WhatsApp
            // routing is detached without forcing the user to dig through code.
            SettingsRow(
                icon = Icons.Outlined.Phone,
                title = stringResource(R.string.profile_phone_label),
                subtitle = stringResource(R.string.profile_phone_row_subtitle),
                value = user?.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.profile_phone_unset),
                onClick = onEditPhone,
            )
            ThinDivider()
            // Notifications expander row
            val statusSubtitle = if (user?.notificationEnabled == false) {
                stringResource(R.string.profile_notifications_row_subtitle_off)
            } else {
                stringResource(R.string.profile_notifications_row_subtitle_on, user?.notificationHour ?: 20)
            }
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.profile_notifications_row_label),
                subtitle = statusSubtitle,
                value = "",
                onClick = { notificationsOpen = !notificationsOpen },
                trailingChevron = true,
            )
            AnimatedVisibility(visible = notificationsOpen) {
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
    title: String,
    subtitle: String,
    value: String,
    onClick: (() -> Unit)?,
    trailingChevron: Boolean = true,
) {
    val rowMod = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowMod.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                // Match the avatar's tinted ring — primaryContainer flips correctly between modes.
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (trailingChevron && onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

// ─── Shared ────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PhoneEditDialog(
    input: String,
    isSaving: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefillText = stringResource(R.string.whatsapp_prefill_text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_phone_label)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.profile_phone_row_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "+51 " hint sits inline with the field — we don't persist a country code
                    // separately yet, the user types the local number and we store it as-is.
                    Text(
                        text = "+51",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        placeholder = { Text(stringResource(R.string.onboarding_phone_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Once the user has a complete-looking number typed, show the WhatsApp
                // launch shortcut as a hint that they can test the linkage right now.
                if (input.length >= 9) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.TextButton(
                        onClick = { ctx.openFluyoOnWhatsApp(prefillText) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.whatsapp_connect_cta),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
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
            // No min-length validation: clearing the field is a valid way to opt out of WhatsApp.
            TextButton(onClick = onConfirm, enabled = !isSaving) {
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

