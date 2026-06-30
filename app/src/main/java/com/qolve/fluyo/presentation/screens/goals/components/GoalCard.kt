package com.qolve.fluyo.presentation.screens.goals.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.domain.model.Goal
import com.qolve.fluyo.presentation.theme.CoralRamp500
// Neutral ramp imports removed in favor of MaterialTheme.colorScheme.* — see ProfileScreen.
import com.qolve.fluyo.presentation.theme.TealRamp500
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Per-goal card on the Metas screen.
 *
 * Layout (per the redesigned mockup):
 *   • 56dp square avatar (accent-tinted, emoji glyph inferred from the goal name).
 *   • Title row: name (bold) on the left, "X meses" duration on the right.
 *   • Amount row: "S/ 540" (bold accent) + "de S/ 800" (muted).
 *   • Chunky 10dp progress bar in the goal's accent color, rounded ends, animated.
 *   • Footer row: 5-dot deposit indicator (filled count = ceil(progress × 5)) + deposit
 *     count label + inline mint "+ Depositar" pill button on the right.
 *
 * **Completed state.** When `goal.isCompleted`, the card gets a coral border, the title-row
 * "X meses" is replaced by "¡completado!", and the footer swaps the dots + deposit button
 * for a "✓ ¡Logrado! Toca para celebrar." line in the goal's accent color.
 */
@Composable
fun GoalCard(
    goal: Goal,
    onClick: () -> Unit,
    onDeposit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = remember(goal.name) { inferGoalVisual(goal.name).second }
    val animatedProgress by animateFloatAsState(
        targetValue = goal.progress,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "goalProgress",
    )

    val today = remember { LocalDate.now() }
    val isCompleted = goal.isCompleted
    val cardBorder = if (isCompleted) BorderStroke(2.dp, CoralRamp500) else null

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title row
            Row(verticalAlignment = Alignment.Top) {
                GoalAvatar(name = goal.name, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = topRightLabel(goal, today),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCompleted) CoralRamp500 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Amount row
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = currencySymbol(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = accent.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "%.0f".format(goal.currentAmount),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "de ${currencySymbol()} %.0f".format(goal.targetAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar — chunky 10dp, color = accent, full-card width.
            ProgressBar(progress = animatedProgress, color = accent)

            Spacer(Modifier.height(12.dp))

            // Footer — either dots + deposits + button OR the completed callout
            if (isCompleted) {
                CompletedFooter(accent = accent)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DepositDots(
                        filledFraction = goal.progress,
                        color = accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = depositCountLabel(goal.depositCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                    )
                    DepositPillButton(onClick = onDeposit)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color),
        )
    }
}

/** 5 small dots — filled count tracks progress. Always shows 5 slots so the row stays stable. */
@Composable
private fun DepositDots(filledFraction: Float, color: Color) {
    val filled = (filledFraction * 5f).toInt().coerceIn(0, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) color else color.copy(alpha = 0.24f)),
            )
        }
    }
}

@Composable
private fun DepositPillButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            // primaryContainer adapts: mint pill in light, deep teal in dark.
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = "+ ${stringResource(R.string.goal_deposit_cta)}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CompletedFooter(accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.goal_card_completed_tap),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
        )
    }
}

/**
 * Top-right corner label.
 *   • Completed → "¡completado!" in coral.
 *   • Deadline + future → "X meses" (or "X semanas" / "X días" when shorter).
 *   • No deadline / past deadline → blank, so the title row reads cleanly.
 */
@Composable
private fun topRightLabel(goal: Goal, today: LocalDate): String {
    if (goal.isCompleted) return stringResource(R.string.goal_card_completed_chip)
    val deadline = goal.deadline ?: return ""
    val daysLeft = ChronoUnit.DAYS.between(today, deadline).toInt()
    if (daysLeft < 0) return "" // overdue handled visually by the bar color elsewhere

    return when {
        daysLeft >= 60 -> {
            val months = daysLeft / 30
            stringResource(R.string.goal_card_months_remaining, months)
        }
        daysLeft >= 30 -> stringResource(R.string.goal_card_one_month_remaining)
        daysLeft >= 14 -> stringResource(R.string.goal_card_weeks_remaining, daysLeft / 7)
        daysLeft > 1 -> stringResource(R.string.goal_card_days_remaining, daysLeft)
        else -> stringResource(R.string.goal_card_one_day_remaining)
    }
}

@Composable
private fun depositCountLabel(count: Int): String = when {
    count == 0 -> stringResource(R.string.goal_card_no_deposits)
    count == 1 -> stringResource(R.string.goal_card_deposits_one)
    else -> stringResource(R.string.goal_card_deposits_other, count)
}

/**
 * Compact one-line variant used in the "Completadas" section of the screen. We don't render
 * this anymore (the main `GoalCard` now handles its completed state internally), but the
 * symbol is kept exported in case other screens want a denser representation.
 */
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
                text = "${currencySymbol()} %.0f".format(goal.targetAmount),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

