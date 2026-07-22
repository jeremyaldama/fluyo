package com.qolve.fluyo.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R

@Composable
fun EmailAuthScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: EmailAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            viewModel.consumeSignedIn()
            onSignedIn()
        }
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Top bar — circular back button, matching the onboarding chrome.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tabs — segmented pill, switches mode.
            ModeTabs(mode = state.mode, onModeChange = viewModel::setMode)

            Spacer(Modifier.height(24.dp))

            // Title + subtitle adapt to mode.
            val titleRes = when (state.mode) {
                AuthMode.SignIn -> R.string.auth_email_title_signin
                AuthMode.SignUp -> R.string.auth_email_title_signup
            }
            val subtitleRes = when (state.mode) {
                AuthMode.SignIn -> R.string.auth_email_subtitle_signin
                AuthMode.SignUp -> R.string.auth_email_subtitle_signup
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (state.confirmationEmail == null) {
                // Form fields.
                if (state.mode == AuthMode.SignUp) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(stringResource(R.string.auth_email_name_label)) },
                        placeholder = { Text(stringResource(R.string.auth_email_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(stringResource(R.string.auth_email_email_label)) },
                    placeholder = { Text(stringResource(R.string.auth_email_email_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.auth_email_password_label)) },
                    placeholder = { Text(stringResource(R.string.auth_email_password_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Inline error mapped from typed AuthFormError.
                state.error?.let { err ->
                    val msg = when (err) {
                        AuthFormError.InvalidEmail -> stringResource(R.string.auth_email_error_invalid_email)
                        AuthFormError.ShortPassword -> stringResource(R.string.auth_email_error_short_password)
                        AuthFormError.MissingName -> stringResource(R.string.auth_email_error_missing_name)
                        is AuthFormError.Server -> err.message
                            ?: stringResource(R.string.login_error_generic)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                ConfirmationPendingContent(
                    state = state,
                    onResend = viewModel::resendConfirmation,
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.confirmationEmail == null) {
                // Submit
                val submitRes = if (state.mode == AuthMode.SignIn) {
                    R.string.auth_email_submit_signin
                } else {
                    R.string.auth_email_submit_signup
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(submitRes),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Mode-switch link
            TextButton(
                onClick = {
                    viewModel.setMode(
                        if (state.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (state.mode == AuthMode.SignIn) {
                            R.string.auth_email_switch_to_signup
                        } else {
                            R.string.auth_email_switch_to_signin
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationPendingContent(
    state: EmailAuthUiState,
    onResend: () -> Unit,
) {
    val email = state.confirmationEmail ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.auth_email_confirmation_required, email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onResend,
            enabled = state.canResendConfirmation,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(27.dp),
        ) {
            if (state.isResendingConfirmation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auth_email_resending_confirmation))
            } else {
                Text(
                    if (state.resendCooldownSeconds > 0) {
                        stringResource(
                            R.string.auth_email_resend_available_in,
                            state.resendCooldownSeconds,
                        )
                    } else {
                        stringResource(R.string.auth_email_resend_confirmation)
                    },
                )
            }
        }
        state.resendFeedback?.let { feedback ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (feedback) {
                        ConfirmationResendFeedback.Sent -> R.string.auth_email_resend_success
                        ConfirmationResendFeedback.Failed -> R.string.auth_email_resend_error
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = when (feedback) {
                    ConfirmationResendFeedback.Sent -> MaterialTheme.colorScheme.primary
                    ConfirmationResendFeedback.Failed -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun ModeTabs(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab(
            label = stringResource(R.string.auth_email_signin_tab),
            selected = mode == AuthMode.SignIn,
            onClick = { onModeChange(AuthMode.SignIn) },
            modifier = Modifier.weight(1f),
        )
        Tab(
            label = stringResource(R.string.auth_email_signup_tab),
            selected = mode == AuthMode.SignUp,
            onClick = { onModeChange(AuthMode.SignUp) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
