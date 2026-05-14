package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealDark
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.util.formatPen
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun HeroHomeCard(
    displayName: String?,
    breakdown: MonthlyBreakdown,
    modifier: Modifier = Modifier,
) {
    val onHeroPrimary = Color.White
    val onHeroSecondary = Color.White.copy(alpha = 0.78f)

    // Time-aware greeting — recomputes once per composition (cheap; users won't notice if it
    // doesn't flip exactly at the boundary, but the next refresh picks it up).
    val greetingHour = remember { LocalTime.now().hour }
    val greeting = remember(greetingHour, displayName) {
        // No @Composable calls inside remember — keys are stable enough.
        greetingHour to displayName
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(FluyoTealLight, FluyoTeal, FluyoTealDark),
                ),
            ),
    ) {
        // Soft decorative blob in the bottom-right corner.
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = timeAwareGreeting(greeting.first, greeting.second),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = onHeroPrimary,
            )
            Text(
                text = stringResource(R.string.home_greeting_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = onHeroSecondary,
            )
            Spacer(Modifier.height(12.dp))

            BudgetCircle(
                breakdown = breakdown,
                size = 160.dp,
                labelColor = onHeroSecondary,
                valueColor = onHeroPrimary,
                captionColor = onHeroSecondary,
                trackOverride = Color.White.copy(alpha = 0.18f),
            )

            Spacer(Modifier.height(14.dp))
            StatusChip(breakdown = breakdown)
        }
    }
}

@Composable
private fun timeAwareGreeting(hour: Int, name: String?): String {
    val (morningNamed, afternoonNamed, eveningNamed) = Triple(
        R.string.home_greeting_morning_named,
        R.string.home_greeting_afternoon_named,
        R.string.home_greeting_evening_named,
    )
    val (morningGeneric, afternoonGeneric, eveningGeneric) = Triple(
        R.string.home_greeting_morning_generic,
        R.string.home_greeting_afternoon_generic,
        R.string.home_greeting_evening_generic,
    )
    val res = when {
        hour < 12 -> if (name != null) morningNamed else morningGeneric
        hour < 19 -> if (name != null) afternoonNamed else afternoonGeneric
        else -> if (name != null) eveningNamed else eveningGeneric
    }
    return if (name != null) stringResource(res, name) else stringResource(res)
}

/**
 * Compact pill below the ring that carries the over/under-budget message.
 * Three states:
 *   • Over budget   → warning icon + "Pasaste tu plan por S/ X"
 *   • On track      → bolt icon + "~S/ Y/día restante" (suggested daily pace)
 *   • No budget set → faint hint to set one (acts as a nudge into profile)
 */
@Composable
private fun StatusChip(breakdown: MonthlyBreakdown) {
    val (icon: ImageVector?, text: String, bg: Color, fg: Color) = when {
        breakdown.monthlyBudget <= 0.0 -> StatusChipData(
            icon = null,
            text = stringResource(R.string.home_no_budget_chip),
            bg = Color.White.copy(alpha = 0.18f),
            fg = Color.White.copy(alpha = 0.9f),
        )
        breakdown.isOverBudget -> StatusChipData(
            icon = Icons.Outlined.WarningAmber,
            text = stringResource(
                R.string.home_over_budget_chip,
                formatPen(-breakdown.remaining),
            ),
            // Coral-tinted but still legible on the teal gradient.
            bg = Color(0xFFFFEBE6),
            fg = Color(0xFFBF360C),
        )
        else -> {
            val today = remember { LocalDate.now() }
            val ym = remember(today) { YearMonth.from(today) }
            val daysLeft = (ym.lengthOfMonth() - today.dayOfMonth).coerceAtLeast(1)
            val daily = breakdown.remaining.coerceAtLeast(0.0) / daysLeft.toDouble()
            StatusChipData(
                icon = Icons.Outlined.Bolt,
                text = stringResource(R.string.home_pace_chip, formatPen(daily)),
                bg = Color.White.copy(alpha = 0.22f),
                fg = Color.White,
            )
        }
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = fg,
        )
    }
}

private data class StatusChipData(
    val icon: ImageVector?,
    val text: String,
    val bg: Color,
    val fg: Color,
)
