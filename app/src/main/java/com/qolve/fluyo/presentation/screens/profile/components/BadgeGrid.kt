package com.qolve.fluyo.presentation.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.util.descriptionRes
import com.qolve.fluyo.presentation.util.icon
import com.qolve.fluyo.presentation.util.nameRes

@Composable
fun BadgeTile(
    type: BadgeType,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (unlocked) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val onBg = if (unlocked) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) FluyoTeal.copy(alpha = 0.20f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = type.icon(),
                    contentDescription = null,
                    tint = onBg,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(type.nameRes()),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = onBg,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(type.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = onBg.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

