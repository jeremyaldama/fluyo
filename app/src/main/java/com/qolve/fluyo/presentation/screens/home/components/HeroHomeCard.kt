package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.MonthlyBreakdown
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealDark
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.util.formatPen

@Composable
fun HeroHomeCard(
    displayName: String?,
    breakdown: MonthlyBreakdown,
    modifier: Modifier = Modifier,
) {
    val onHeroPrimary = Color.White
    val onHeroSecondary = Color.White.copy(alpha = 0.78f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(FluyoTealLight, FluyoTeal, FluyoTealDark),
                ),
            ),
    ) {
        // Soft decorative blob in the bottom-right corner.
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val greeting = displayName?.let { stringResource(R.string.home_greeting_named, it) }
                ?: stringResource(R.string.home_greeting_generic)
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = onHeroPrimary,
            )
            Text(
                text = stringResource(R.string.home_greeting_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = onHeroSecondary,
            )
            Spacer(Modifier.height(16.dp))

            BudgetCircle(
                breakdown = breakdown,
                labelColor = onHeroSecondary,
                valueColor = onHeroPrimary,
                captionColor = onHeroSecondary,
                trackOverride = Color.White.copy(alpha = 0.18f),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.home_spent_so_far,
                    formatPen(breakdown.totalSpent),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = onHeroSecondary,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
