package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.presentation.util.formatPen
import com.qolve.fluyo.presentation.util.iconForToken
import com.qolve.fluyo.presentation.util.parseHexColor
import androidx.compose.ui.res.stringResource

@Composable
fun ExpenseRow(
    expense: Expense,
    category: Category?,
    modifier: Modifier = Modifier,
) {
    val swatchColor = category?.let { parseHexColor(it.color) } ?: MaterialTheme.colorScheme.outline
    val icon = iconForToken(category?.icon ?: "tag")

    Row(
        modifier = modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(swatchColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = swatchColor,
            )
        }
        Spacer(Modifier.width(12.dp))
        // Hierarchy: title = description or category; subtitle = recipient (Yape destinatario) when present.
        // Critically: do NOT show category name as subtitle when it already IS the title — that produces
        // "Comida / Comida" duplicates on quick manual entries with no description.
        val categoryName = category?.name
        val descriptionTitle = expense.description?.takeIf { it.isNotBlank() }
        val titleText = descriptionTitle
            ?: categoryName
            ?: stringResource(R.string.expense_untitled)
        val subtitleText: String? = when {
            !expense.recipient.isNullOrBlank() -> expense.recipient
            // Show category as subtitle only when the title was the description (so it's NOT already shown).
            descriptionTitle != null && categoryName != null -> categoryName
            else -> null
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatPen(expense.amount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Suppress("unused")
private val previewMarker: Color = Color.Transparent
