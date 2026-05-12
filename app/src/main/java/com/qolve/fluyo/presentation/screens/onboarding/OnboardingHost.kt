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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qolve.fluyo.R

@Composable
fun OnboardingHost(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            StepIndicator(currentStep = state.step, totalSteps = OnboardingViewModel.LAST_STEP + 1)

            Spacer(Modifier.height(24.dp))

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
                        else -> TourStep(
                            phone = state.phoneInput,
                            onPhoneChange = viewModel::onPhoneChange,
                        )
                    }
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::back,
                    enabled = state.step > 0 && !state.isSaving,
                ) {
                    Text(stringResource(R.string.action_back))
                }

                val isLastStep = state.step == OnboardingViewModel.LAST_STEP
                val canAdvance = when (state.step) {
                    0 -> state.canAdvanceFromBudget
                    else -> true
                }

                Button(
                    onClick = {
                        if (isLastStep) viewModel.finish() else viewModel.next()
                    },
                    enabled = canAdvance && !state.isSaving,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (isLastStep) R.string.action_finish else R.string.action_continue,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until totalSteps) {
            val color = if (i <= currentStep) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
