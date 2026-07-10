package com.qolve.fluyo.presentation.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.BrandDot
import com.qolve.fluyo.presentation.theme.CoralRamp100
import com.qolve.fluyo.presentation.theme.CoralRamp500
import com.qolve.fluyo.presentation.theme.FluyoSpecialty
// Theme-driven colors replace raw NeutralRamp refs so dark mode renders correctly.
import com.qolve.fluyo.presentation.theme.TealRamp500

/**
 * Bottom sheet shown when the user taps the home FAB.
 *
 * Layout (per the redesigned mockup):
 *   • Title row — bold "Nuevo gasto" on the left, serif italic "¿cómo lo metes?" on the right.
 *   • Primary CTA — large teal pill labeled "Escanear captura". This is the recommended path
 *     (OCR on a Yape screenshot) and gets the most visual weight.
 *   • Secondary tiles — two square cards side-by-side for Manual and Voz, each with an icon
 *     and a one-line hint of what that flow feels like ("tecla a tecla", "15 soles en el menú").
 *   • Coral tip card — friendly nudge about the WhatsApp / Yape forwarding flow.
 *
 * The Voz tile is wired but currently disabled (the voice transcription pipeline lives in
 * the WhatsApp backend, not the Android app). It's rendered with reduced opacity rather
 * than hidden so users learn the option exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onScan: () -> Unit,
    onCamera: () -> Unit,
    onVoice: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Title row
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.add_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.add_sheet_subtitle),
                    style = FluyoSpecialty.taglineSerif.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            // Primary CTA — big teal pill, full width (gallery), with a camera shortcut
            ScanPrimaryCta(onClick = onScan, onCamera = onCamera)

            // Secondary tiles row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryTile(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.add_option_manual_short),
                    hint = stringResource(R.string.add_option_manual_hint),
                    enabled = true,
                    onClick = onManual,
                    modifier = Modifier.weight(1f),
                )
                SecondaryTile(
                    icon = Icons.Outlined.Mic,
                    label = stringResource(R.string.add_option_voice_short),
                    hint = stringResource(R.string.add_option_voice_hint),
                    enabled = true,
                    onClick = onVoice,
                    modifier = Modifier.weight(1f),
                )
            }

            YapeTipCard()
        }
    }
}

@Composable
private fun ScanPrimaryCta(onClick: () -> Unit, onCamera: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(TealRamp500),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Main area — pick the Yape/Plin screenshot from the gallery (the common case).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.add_option_scan_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Text(
                        text = stringResource(R.string.add_option_scan_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            // Small accent dot at the bottom-right corner — the recurring brand motif.
            BrandDot(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                size = 8.dp,
                color = CoralRamp500,
            )
        }
        // Camera shortcut — photograph a physical receipt directly.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(vertical = 18.dp)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(64.dp)
                .clickable(onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = stringResource(R.string.add_option_scan_camera),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun SecondaryTile(
    icon: ImageVector,
    label: String,
    hint: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileModifier = if (enabled) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        modifier = tileModifier.height(112.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = if (enabled) hint else stringResource(R.string.add_option_coming_soon),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun YapeTipCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CoralRamp100)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(CoralRamp500),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = parseHtmlBoldTip(stringResource(R.string.add_sheet_yape_tip)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The tip string contains `<b>Tip:</b>` HTML markup that we parse into a span style here.
 * Cheap parser — sufficient for one fixed marker.
 */
@Composable
private fun parseHtmlBoldTip(raw: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        val openIdx = raw.indexOf("<b>", i)
        if (openIdx == -1) {
            append(raw.substring(i))
            break
        }
        append(raw.substring(i, openIdx))
        val closeIdx = raw.indexOf("</b>", openIdx)
        if (closeIdx == -1) {
            append(raw.substring(openIdx))
            break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(raw.substring(openIdx + 3, closeIdx))
        }
        i = closeIdx + 4
    }
}

