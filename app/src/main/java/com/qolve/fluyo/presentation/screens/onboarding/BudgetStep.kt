package com.qolve.fluyo.presentation.screens.onboarding

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.util.currencySymbol
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.presentation.theme.FluyoTeal
import java.text.NumberFormat
import java.util.Locale
import java.math.RoundingMode

/**
 * Onboarding step 1 — pick monthly budget.
 *
 * Visual language (per the mockup):
 *   • Large bold title + muted subtitle (set by parent host).
 *   • White amount card: "PRESUPUESTO MENSUAL" eyebrow, "S/" prefix in slightly smaller weight,
 *     huge integer part, ".00" suffix in smaller text, and a mint "≈ S/X al día" chip.
 *   • Quick-pick chips for common amounts (S/600 / S/900 / S/1.2K / S/1.8K) — selected = teal.
 *   • Helper text with the Lima university student average (anchor expectation).
 */
@Composable
fun BudgetStep(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_budget_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_budget_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // "Safe floor" guidance for irregular incomes: the budget is a spending
        // plan, not an income forecast — start with what's certain, adjust later.
        Text(
            text = stringResource(R.string.onboarding_budget_guidance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(24.dp))

        AmountCard(value = value, onValueChange = onValueChange)

        Spacer(Modifier.height(16.dp))

        QuickChips(value = value, onPick = onValueChange)

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_budget_lima_avg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AmountCard(value: String, onValueChange: (String) -> Unit) {
    val parsed = MoneyAmount.parse(value, RoundingMode.UNNECESSARY) ?: MoneyAmount.ZERO
    val integerPart = parsed.cents / 100L
    val perDay = parsed.dividedBy(30L, RoundingMode.HALF_EVEN)
    val integerFormatted = remember(integerPart) {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE")).format(integerPart)
    }
    val perDayFormatted = remember(perDay) {
        "S/ " + NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE")).apply {
            maximumFractionDigits = 0
            roundingMode = RoundingMode.HALF_EVEN
        }.format(perDay.toBigDecimal())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        // surface adapts: pure white in light, dark grey card in dark.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_budget_card_label),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = currencySymbol(),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.width(4.dp))
                // Editable integer part — BasicTextField gives us full typographic control.
                BasicTextField(
                    value = if (value.isBlank()) "0" else integerFormatted,
                    onValueChange = { typed ->
                        // Strip everything except digits — formatter re-inserts grouping.
                        val digits = typed.filter { it.isDigit() }.trimStart('0')
                        onValueChange(digits.ifEmpty { "0" })
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(FluyoTeal),
                    textStyle = TextStyle(
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.width(180.dp),
                )
                Text(
                    text = ".00",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    ),
                    modifier = Modifier.padding(top = 26.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            // Daily-equivalent chip.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FluyoTeal.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "≈ $perDayFormatted al día",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = FluyoTeal,
                )
            }
        }
    }
}

@Composable
private fun QuickChips(value: String, onPick: (String) -> Unit) {
    val selected = MoneyAmount.parse(value, RoundingMode.UNNECESSARY)
        ?.takeIf { it.cents % 100L == 0L }
        ?.let { it.cents / 100L }
    val choices = listOf(
        ChipChoice(600L, R.string.onboarding_budget_chip_600),
        ChipChoice(900L, R.string.onboarding_budget_chip_900),
        ChipChoice(1200L, R.string.onboarding_budget_chip_1200),
        ChipChoice(1800L, R.string.onboarding_budget_chip_1800),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEach { choice ->
            val isSelected = selected == choice.value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) FluyoTeal.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onPick(choice.value.toString()) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(choice.labelRes),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isSelected) FluyoTeal else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private data class ChipChoice(val value: Long, val labelRes: Int)
