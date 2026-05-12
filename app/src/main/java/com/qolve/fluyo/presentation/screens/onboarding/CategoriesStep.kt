package com.qolve.fluyo.presentation.screens.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R

private data class CategoryPreview(
    val nameRes: Int,
    val icon: ImageVector,
    val color: Color,
)

private val DefaultCategories = listOf(
    CategoryPreview(R.string.cat_food, Icons.Outlined.Restaurant, Color(0xFFFF7043)),
    CategoryPreview(R.string.cat_transport, Icons.Outlined.DirectionsBus, Color(0xFF42A5F5)),
    CategoryPreview(R.string.cat_entertainment, Icons.Outlined.SportsEsports, Color(0xFFAB47BC)),
    CategoryPreview(R.string.cat_snacks, Icons.Outlined.Coffee, Color(0xFFFFA726)),
    CategoryPreview(R.string.cat_health, Icons.Outlined.Favorite, Color(0xFFEF5350)),
    CategoryPreview(R.string.cat_education, Icons.Outlined.Book, Color(0xFF26A69A)),
    CategoryPreview(R.string.cat_other, Icons.Outlined.MoreHoriz, Color(0xFF78909C)),
)

@Composable
fun CategoriesStep() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_step_categories_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_step_categories_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DefaultCategories.forEach { cat ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(cat.color.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = cat.color,
                            )
                        }
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = stringResource(cat.nameRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
