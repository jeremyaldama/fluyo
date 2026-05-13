package com.qolve.fluyo.presentation.screens.auth

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoCyan
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.theme.FluyoTheme
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle

@Composable
fun LoginScreen(
    composeAuth: ComposeAuth,
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
    )
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onGoogleClick: () -> Unit,
) {
    Scaffold { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            BackgroundBlobs()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(0.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FluyoWordmark()
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

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
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (uiState is LoginUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.login_continue_google),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.login_legal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundBlobs() {
    // Two soft brand-tinted blobs in opposite corners. Decorative only.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-120).dp, y = (-120).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            FluyoCyan.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(160f, 160f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 140.dp, y = 140.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            FluyoCoral.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun FluyoWordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Tiny flow-line glyph to the left of the wordmark.
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 28.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(FluyoTeal, FluyoTealLight),
                    ),
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Fluyo",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                letterSpacing = (-1).sp,
            ),
            color = FluyoTeal,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    FluyoTheme {
        LoginContent(uiState = LoginUiState.Idle, onGoogleClick = {})
    }
}
