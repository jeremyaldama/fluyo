package com.qolve.fluyo.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoTeal

/**
 * Onboarding step 3 — optional WhatsApp linkage + Yape forwarding hint.
 *
 * Visual language from the mockup:
 *   • Mint-tinted primary card. Header row: dark-teal circle with WhatsApp glyph, title, subtitle.
 *     White inner row: Peru flag + "+51" + numeric input.
 *   • Coral-tinted secondary card explaining the Yape screenshot-forwarding flow as a teaser.
 */
@Composable
fun WhatsAppStep(
    phone: String,
    onPhoneChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_whatsapp_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF111111),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_whatsapp_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF606060),
        )

        Spacer(Modifier.height(24.dp))

        WhatsAppCard(phone = phone, onPhoneChange = onPhoneChange)

        Spacer(Modifier.height(12.dp))

        YapeHintCard()
    }
}

@Composable
private fun WhatsAppCard(phone: String, onPhoneChange: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = FluyoTeal.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(FluyoTeal),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.onboarding_whatsapp_card_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF111111),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_whatsapp_card_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6A6A6A),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // White inner row with the Peru flag + +51 prefix + numeric input.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PeruFlag()
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "+51",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF111111),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = phone,
                    onValueChange = { onPhoneChange(it.filter(Char::isDigit).take(9)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    cursorBrush = SolidColor(FluyoTeal),
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111111),
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (phone.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.onboarding_whatsapp_phone_hint),
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB0B0B0),
                                    ),
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun YapeHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    .background(FluyoCoral.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = FluyoCoral,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.onboarding_yape_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF111111),
                )
                Text(
                    text = stringResource(R.string.onboarding_yape_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6A6A6A),
                )
            }
        }
    }
}

/**
 * Peru flag — three vertical stripes (red / white / red). No SVG asset needed; this is good
 * enough at 24×16 dp inline with text.
 */
@Composable
private fun PeruFlag() {
    Row(
        modifier = Modifier
            .size(width = 24.dp, height = 16.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        // RowScope.weight + fillMaxHeight gives three equal vertical stripes.
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFD91023)))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFD91023)))
    }
}
