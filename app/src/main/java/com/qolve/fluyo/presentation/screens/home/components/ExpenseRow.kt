package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.presentation.util.money
import com.qolve.fluyo.presentation.util.iconForToken
import com.qolve.fluyo.presentation.util.parseHexColor
import com.qolve.fluyo.presentation.util.relativeTimeFrom
import androidx.compose.ui.res.stringResource

@Composable
fun ExpenseRow(
    expense: Expense,
    category: Category?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val swatchColor = category?.let { parseHexColor(it.color) } ?: MaterialTheme.colorScheme.outline
    val icon = iconForToken(category?.icon ?: "tag")

    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
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
        // Title prefers the user's own description, falling back to the recipient (Yape
        // destinatario) and then the category name. Subtitle is always "source · hace Xh"
        // per the redesigned mockup — a much clearer signal than echoing the category.
        val titleText = expense.description?.takeIf { it.isNotBlank() }
            ?: expense.recipient?.takeIf { it.isNotBlank() }
            ?: category?.name
            ?: stringResource(R.string.expense_untitled)
        val sourceLabel = when (expense.source) {
            ExpenseSource.MANUAL -> stringResource(R.string.expense_source_manual)
            ExpenseSource.OCR -> stringResource(R.string.expense_source_ocr)
            ExpenseSource.VOICE -> stringResource(R.string.expense_source_voice)
            ExpenseSource.WHATSAPP -> stringResource(R.string.expense_source_whatsapp)
            ExpenseSource.EMAIL -> stringResource(R.string.expense_source_email)
        }
        val relative = relativeTimeFrom(expense.createdAt)
        val subtitleText = "$sourceLabel · $relative"

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
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = money(expense.amount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Suppress("unused")
private val previewMarker: Color = Color.Transparent
