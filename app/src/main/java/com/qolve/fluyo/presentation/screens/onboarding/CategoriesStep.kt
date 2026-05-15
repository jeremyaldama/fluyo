package com.qolve.fluyo.presentation.screens.onboarding

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TagFaces
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.FluyoTeal

/**
 * Onboarding step 2 — preview of the seven default categories the DB trigger creates
 * (per `seed_default_categories` in CLAUDE.md). The mockup shows six visual cards instead of
 * a vertical list, so we surface six of those defaults in a 2-column grid. The user doesn't
 * pick anything here — it's pure expectation-setting before they hit Continuar.
 */
@Composable
fun CategoriesStep() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_categories_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_categories_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        val previews = remember()
        // Render manually as rows of 2 — the parent host already uses an AnimatedContent
        // that constrains height, and nesting a LazyVerticalGrid here would force a fixed
        // height. With only 6 items, manual chunks are simpler and lay out exactly like the mockup.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            previews.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { preview ->
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryPreviewCard(preview)
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = FluyoTeal,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.onboarding_categories_edit_later),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FluyoTeal,
            )
        }
    }
}

@Composable
private fun CategoryPreviewCard(preview: CategoryPreview) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(preview.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = preview.icon,
                    contentDescription = null,
                    tint = preview.color,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(preview.nameRes),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(preview.hintRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private data class CategoryPreview(
    val nameRes: Int,
    val hintRes: Int,
    val icon: ImageVector,
    val color: Color,
)

/**
 * Static preview list. Wraps `remember { ... }` so the list is allocated once per composition.
 * The colors are tuned to match the mockup's tinted icon squares (coral / blue / pink / green /
 * purple / mint).
 */
@Composable
private fun remember(): List<CategoryPreview> = androidx.compose.runtime.remember {
    listOf(
        CategoryPreview(
            nameRes = R.string.cat_food,
            hintRes = R.string.onboarding_cat_food_hint,
            icon = Icons.Outlined.Restaurant,
            color = Color(0xFFFF7043),
        ),
        CategoryPreview(
            nameRes = R.string.cat_transport,
            hintRes = R.string.onboarding_cat_transport_hint,
            icon = Icons.Outlined.DirectionsBus,
            color = Color(0xFF42A5F5),
        ),
        CategoryPreview(
            nameRes = R.string.onboarding_cat_fun,
            hintRes = R.string.onboarding_cat_fun_hint,
            icon = Icons.Outlined.TagFaces,
            color = Color(0xFFAB47BC),
        ),
        CategoryPreview(
            nameRes = R.string.onboarding_cat_shopping,
            hintRes = R.string.onboarding_cat_shopping_hint,
            icon = Icons.Outlined.ShoppingBag,
            color = Color(0xFF66BB6A),
        ),
        CategoryPreview(
            nameRes = R.string.onboarding_cat_studies,
            hintRes = R.string.onboarding_cat_studies_hint,
            icon = Icons.Outlined.School,
            color = Color(0xFF7E57C2),
        ),
        CategoryPreview(
            nameRes = R.string.onboarding_cat_home,
            hintRes = R.string.onboarding_cat_home_hint,
            icon = Icons.Outlined.Home,
            color = Color(0xFF26A69A),
        ),
    )
}
