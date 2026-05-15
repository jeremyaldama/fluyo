package com.qolve.fluyo.presentation.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.theme.FluyoTheme
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle

@Composable
fun LoginScreen(
    composeAuth: ComposeAuth,
    onUseEmailPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val googleAction = composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                is NativeSignInResult.Success -> viewModel.onSignInSucceeded()
                is NativeSignInResult.ClosedByUser -> viewModel.reset()
                is NativeSignInResult.NetworkError -> viewModel.onSignInFailed(result.message)
                is NativeSignInResult.Error -> viewModel.onSignInFailed(result.message)
            }
        },
    )

    LoginContent(
        uiState = uiState,
        onGoogleClick = {
            viewModel.onSignInStarted()
            googleAction.startFlow()
        },
        onUseEmailPassword = onUseEmailPassword,
    )
}

// Page background — pale mint, matches the mockup.
// Canvas color removed — now sourced from MaterialTheme.colorScheme.background so the
// login screen inherits the dark canvas in dark mode (was a fixed pale mint before).

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onGoogleClick: () -> Unit,
    onUseEmailPassword: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(MaterialTheme.colorScheme.background),
        ) {
            DecorativeWatermarks()
            DecorativeDots()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(0.dp))

                // Brand block — logo card + wordmark + tagline.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LogoCard()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Fluyo",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            letterSpacing = (-1).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.login_tagline),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                // CTA block at bottom.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (uiState is LoginUiState.Error && uiState.message != null) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }

                    Button(
                        onClick = onGoogleClick,
                        enabled = uiState !is LoginUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FluyoTeal,
                            contentColor = Color.White,
                        ),
                    ) {
                        if (uiState is LoginUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            // Google mark — minimal disc with white "G" so we don't have to ship a vector.
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "G",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FluyoTeal,
                                    ),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.login_continue_google),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = legalAnnotated(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onUseEmailPassword,
                    ) {
                        Text(
                            text = stringResource(R.string.login_continue_email),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = FluyoTeal,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds the legal text with the "términos" and "política de privacidad" words underlined.
 * The strings.xml entry uses literal `<u>…</u>` markers; we transform them into SpanStyles
 * here so the underlines render in the right color.
 */
@Composable
private fun legalAnnotated(): AnnotatedString {
    val raw = stringResource(R.string.login_legal)
    return buildAnnotatedString {
        // Simple <u>...</u> replacement — sufficient for two static markers in this string.
        var i = 0
        while (i < raw.length) {
            val openIdx = raw.indexOf("<u>", i)
            if (openIdx == -1) {
                append(raw.substring(i))
                break
            }
            append(raw.substring(i, openIdx))
            val closeIdx = raw.indexOf("</u>", openIdx)
            if (closeIdx == -1) {
                append(raw.substring(openIdx))
                break
            }
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(raw.substring(openIdx + 3, closeIdx))
            }
            i = closeIdx + 4
        }
    }
}

/**
 * The square "F." logo card from the mockup — teal rounded square, white "F" letter, coral dot
 * after the F. Matches the launcher icon visual language.
 */
@Composable
private fun LogoCard() {
    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(FluyoTeal),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "F",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.sp,
                    color = Color.White,
                ),
            )
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = (-6).dp)
                    .clip(CircleShape)
                    .background(FluyoCoral),
            )
        }
    }
}

/**
 * Big faded "F." letters as background watermarks, one in each diagonal corner.
 * Soft and brand-tinted so they read as texture, not content.
 */
@Composable
private fun DecorativeWatermarks() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "F.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 120.sp,
            ),
            color = FluyoTealLight.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-8).dp, y = 24.dp),
        )
        Text(
            text = "F.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 120.sp,
            ),
            color = FluyoTealLight.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 80.dp, y = 20.dp)
                .rotate(0f),
        )
    }
}

/**
 * Three tiny accent dots scattered around the brand block. Brand-tinted, alternating teal/coral.
 */
@Composable
private fun DecorativeDots() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Teal dot left of the logo, mid-height.
        drawCircle(
            color = FluyoTeal.copy(alpha = 0.45f),
            radius = 5f,
            center = Offset(w * 0.10f, h * 0.32f),
        )
        // Coral dot right of the wordmark.
        drawCircle(
            color = FluyoCoral.copy(alpha = 0.55f),
            radius = 5f,
            center = Offset(w * 0.78f, h * 0.18f),
        )
        // Tiny teal dot lower-left.
        drawCircle(
            color = FluyoTeal.copy(alpha = 0.40f),
            radius = 4f,
            center = Offset(w * 0.06f, h * 0.46f),
        )
        // Tiny coral dot below the wordmark.
        drawCircle(
            color = FluyoCoral.copy(alpha = 0.45f),
            radius = 4f,
            center = Offset(w * 0.46f, h * 0.50f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    FluyoTheme {
        LoginContent(
            uiState = LoginUiState.Idle,
            onGoogleClick = {},
            onUseEmailPassword = {},
        )
    }
}

@Suppress("unused")
private val previewMarker: SolidColor = SolidColor(Color.Transparent)
