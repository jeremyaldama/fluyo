package com.qolve.fluyo.presentation.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.util.openFluyoOnWhatsApp

// Canvas color removed — Scaffold now reads from MaterialTheme.colorScheme.background so
// dark mode gets the dark canvas, light mode the pale mint.

@Composable
fun OnboardingHost(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.whatsAppLaunchEvents.collect { request ->
            context.openFluyoOnWhatsApp(request.message)
        }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Top row — progress pills (left) + Saltar (right).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepPills(
                    currentStep = state.step,
                    totalSteps = OnboardingViewModel.lastStep + 1,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = viewModel::skip,
                    enabled = !state.isSaving,
                ) {
                    Text(
                        text = stringResource(R.string.action_skip_short),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Step counter label — small teal eyebrow.
            Text(
                text = stringResource(
                    R.string.onboarding_step_of,
                    state.step + 1,
                    OnboardingViewModel.lastStep + 1,
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                ),
                color = FluyoTeal,
            )

            Spacer(Modifier.height(8.dp))

            // Content area — slides horizontally between steps.
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut()) using SizeTransform(clip = false)
                        } else {
                            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 4 } + fadeOut()) using SizeTransform(clip = false)
                        }
                    },
                    label = "onboardingStep",
                ) { step ->
                    when (step) {
                        0 -> BudgetStep(
                            value = state.budgetInput,
                            onValueChange = viewModel::onBudgetChange,
                        )
                        1 -> CategoriesStep()
                        else -> WhatsAppStep(
                            link = state.whatsAppLink,
                            isWorking = state.isLoadingWhatsApp || state.isCreatingWhatsAppChallenge,
                            challengeExpiresAt = state.whatsAppChallengeExpiresAt,
                            onConnect = viewModel::createWhatsAppChallenge,
                            onRefresh = viewModel::refreshWhatsAppLink,
                        )
                    }
                }
            }

            // Inline error
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Bottom row — back circle (when applicable) + pill CTA.
            BottomBar(
                showBack = state.step > 0,
                isLastStep = state.step == OnboardingViewModel.lastStep,
                canAdvance = canAdvance(state),
                isSaving = state.isSaving,
                onBack = viewModel::back,
                onAdvance = {
                    if (state.step == OnboardingViewModel.lastStep) viewModel.finish() else viewModel.next()
                },
            )
        }
    }
}

private fun canAdvance(state: OnboardingUiState): Boolean = when (state.step) {
    0 -> state.canAdvanceFromBudget
    else -> true
}

@Composable
private fun StepPills(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            val isPast = i < currentStep
            val (color, width) = when {
                isActive -> FluyoTeal to 26.dp
                isPast -> FluyoTeal.copy(alpha = 0.5f) to 18.dp
                // Unfilled pill — outlineVariant flips per mode so it stays subtly visible.
                else -> MaterialTheme.colorScheme.outlineVariant to 18.dp
            }
            Box(
                modifier = Modifier
                    .size(width = width, height = 4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun BottomBar(
    showBack: Boolean,
    isLastStep: Boolean,
    canAdvance: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onAdvance: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                enabled = !isSaving,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Button(
            onClick = onAdvance,
            enabled = canAdvance && !isSaving,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = RoundedCornerShape(27.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FluyoTeal,
                contentColor = Color.White,
            ),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(
                        when {
                            isLastStep -> R.string.onboarding_action_start
                            else -> R.string.action_continue
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
