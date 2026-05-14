package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.BrandDot
import com.qolve.fluyo.presentation.theme.CoralRamp100
import com.qolve.fluyo.presentation.theme.CoralRamp700
import com.qolve.fluyo.presentation.theme.FluyoSpecialty
import com.qolve.fluyo.presentation.theme.NeutralRamp900
import com.qolve.fluyo.presentation.theme.TealRamp500
import java.util.Locale

/**
 * Top strip of the Home screen.
 *
 * Layout (per the Fluyo mockup):
 *   • Left — the F-mark (teal 16dp square with the coral brand dot) above a two-line stack:
 *     "Hola," in serif italic and the user's first name in bold sans.
 *   • Right — circular avatar. Falls back to colored initials if no photo URL is provided.
 *
 * The whole row sits flush with the screen edge — the screen owns its own gutters.
 */
@Composable
fun HomeHeader(
    displayName: String?,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FMark()
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting_eyebrow),
                style = FluyoSpecialty.taglineSerif.copy(fontSize = 16.sp),
                color = NeutralRamp900,
            )
            Text(
                text = displayName ?: stringResource(R.string.profile_default_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = NeutralRamp900,
            )
        }
        AvatarChip(displayName = displayName, photoUrl = avatarUrl, onClick = onAvatarClick)
    }
}

/**
 * The Fluyo F-mark glyph. Mini version of the launcher icon — a small teal letter with a
 * coral accent dot. Reused here as the topmost identity element.
 */
@Composable
private fun FMark() {
    Box(modifier = Modifier.size(width = 22.dp, height = 26.dp)) {
        Text(
            text = "F.",
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TealRamp500,
            ),
        )
        // Coral overlay on the dot in "F." — covers the typographic dot with the brand motif.
        BrandDot(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-3).dp),
            size = 5.dp,
            color = com.qolve.fluyo.presentation.theme.CoralRamp500,
        )
    }
}

/**
 * Circular avatar in the top-right corner. Uses the same deterministic initials-color
 * helper as the Profile screen so the same user always looks identical here and there.
 *
 * Tapping it is a hint to navigate to Profile — but the home screen doesn't actually own
 * navigation, so we expose the click as a callback the parent wires up.
 */
@Composable
private fun AvatarChip(
    displayName: String?,
    photoUrl: String?,
    onClick: () -> Unit,
) {
    val initials = remember(displayName) { initialsOf(displayName) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(CoralRamp100)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = CoralRamp700,
            )
        }
    }
}

private fun initialsOf(name: String?): String {
    if (name.isNullOrBlank()) return "?"
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return parts.firstOrNull()
        ?.firstOrNull()
        ?.toString()
        ?.uppercase(Locale.ROOT)
        ?: "?"
}

@Suppress("unused")
private val previewSwatch: Color = Color.Transparent
