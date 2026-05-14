package com.qolve.fluyo.presentation.screens.goals.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.presentation.util.formatPen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Rich goal card used in the "Mis metas" list.
 *
 * Visual language goals (matching the brief):
 *   • Personality: colored emoji avatar (inferred from the goal name in [GoalAvatar]).
 *   • Progress: chunky animated brand-colored bar (10dp), not a thin Material line.
 *   • Urgency: days-remaining chip + a "daily pace to make it" hint when there's a deadline.
 *   • Recovery: amber "Plazo vencido" chip when the deadline has passed — never punitive red.
 *   • Near-completion: at ≥80%, a subtle gradient glow makes the card feel ready to finish.
 *   • Action: inline `Aportar` outlined button so users can deposit without leaving the list.
 */
@Composable
fun GoalCard(
    goal: Goal,
    onClick: () -> Unit,
    onDeposit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = goal.progress,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "goalProgress",
    )

    val accent = remember(goal.name) { inferGoalVisual(goal.name).second }
    val isNearComplete = goal.progress >= 0.8f && !goal.isCompleted
    val today = remember { LocalDate.now() }
    val daysLeft = goal.deadline?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val isOverdue = daysLeft != null && daysLeft < 0 && !goal.isCompleted

    // Containers: highlight near-complete with a brand-tinted gradient, otherwise neutral surface.
    val containerColor = MaterialTheme.colorScheme.surface
    val containerBrush = if (isNearComplete) {
        Brush.linearGradient(
            colors = listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.04f)),
        )
    } else null

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            containerBrush?.let {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(it),
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Top row: avatar + name/target + percent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalAvatar(name = goal.name, size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.goal_target_inline, formatPen(goal.targetAmount)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PercentBadge(
                        percent = (goal.progress * 100f).toInt(),
                        accent = accent,
                        isHero = isNearComplete,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Custom progress bar (not LinearProgressIndicator) — chunky, rounded, brand-colored.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(accent),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Amount row: current bold left, "de target" muted right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatPen(goal.currentAmount),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                    if (goal.remaining > 0.0) {
                        Text(
                            text = "Faltan ${formatPen(goal.remaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom row: chips on the left, "Aportar" button on the right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomChips(
                        goal = goal,
                        accent = accent,
                        daysLeft = daysLeft,
                        isOverdue = isOverdue,
                        isNearComplete = isNearComplete,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onDeposit,
                        shape = RoundedCornerShape(50),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 6.dp,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.goal_deposit_cta),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PercentBadge(percent: Int, accent: Color, isHero: Boolean) {
    val bg = if (isHero) accent else accent.copy(alpha = 0.14f)
    val fg = if (isHero) Color.White else accent
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.goal_progress_percent, percent),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = fg,
        )
    }
}

@Composable
private fun BottomChips(
    goal: Goal,
    accent: Color,
    daysLeft: Int?,
    isOverdue: Boolean,
    isNearComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val deadlineLabel = goal.deadline?.format(deadlineFmt)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isNearComplete -> Chip(
                icon = Icons.Outlined.AutoAwesome,
                text = stringResource(R.string.goal_complete_chip),
                bg = accent.copy(alpha = 0.18f),
                fg = accent,
            )
            isOverdue -> Chip(
                icon = Icons.Outlined.WarningAmber,
                text = stringResource(R.string.goal_overdue),
                bg = Color(0xFFFFF3E0),
                fg = Color(0xFFE65100),
            )
            daysLeft != null -> {
                val label = if (daysLeft == 1) {
                    stringResource(R.string.goal_days_left_one)
                } else {
                    stringResource(R.string.goal_days_left_other, daysLeft)
                }
                Chip(
                    icon = Icons.Outlined.Schedule,
                    text = label,
                    bg = MaterialTheme.colorScheme.surfaceVariant,
                    fg = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Pace hint sits next to the day chip when there's time + money to go.
                if (daysLeft > 0 && goal.remaining > 0.0) {
                    val pace = goal.remaining / daysLeft.toDouble()
                    Text(
                        text = stringResource(R.string.goal_pace_hint, formatPen(pace)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            deadlineLabel != null -> Chip(
                icon = Icons.Outlined.Schedule,
                text = deadlineLabel,
                bg = MaterialTheme.colorScheme.surfaceVariant,
                fg = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, bg: Color, fg: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun CompletedGoalRow(goal: Goal, modifier: Modifier = Modifier) {
    val accent = remember(goal.name) { inferGoalVisual(goal.name).second }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalAvatar(name = goal.name, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = goal.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatPen(goal.targetAmount),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val deadlineFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"))
