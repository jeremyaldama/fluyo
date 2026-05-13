package com.qolve.fluyo.presentation.screens.profile.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.UserLevel
import com.qolve.fluyo.presentation.util.levelNameRes

@Composable
fun LevelCard(
    level: UserLevel,
    totalPoints: Int,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = level.progressTo(totalPoints),
        animationSpec = tween(700),
        label = "levelProgress",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.profile_level_label, level.number),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = stringResource(R.string.profile_points, totalPoints),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = stringResource(levelNameRes(level.key)),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(0.dp))
            val ceiling = level.nextThreshold
            Text(
                text = if (ceiling != null) {
                    stringResource(R.string.profile_points_to_next, (ceiling - totalPoints).coerceAtLeast(0))
                } else {
                    stringResource(R.string.profile_max_level)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}
