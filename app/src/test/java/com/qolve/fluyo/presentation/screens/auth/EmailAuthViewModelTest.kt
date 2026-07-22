package com.qolve.fluyo.presentation.screens.auth

import com.qolve.fluyo.domain.model.SignUpOutcome
import com.qolve.fluyo.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signup requiring confirmation does not provision without a session`() = runTest(dispatcher) {
        coEvery { repository.signUpWithEmail(any(), any(), any()) } returns
            Result.success(SignUpOutcome.ConfirmationRequired("person@example.com"))
        val viewModel = validSignUpViewModel()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("person@example.com", viewModel.uiState.value.confirmationEmail)
        assertFalse(viewModel.uiState.value.signedIn)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.onPasswordChange("must-not-be-retained")
        assertEquals("", viewModel.uiState.value.password)
        coVerify(exactly = 0) { repository.ensureUserRow() }
    }

    @Test
    fun `signup with immediate session delegates provisioning to root`() = runTest(dispatcher) {
        coEvery { repository.signUpWithEmail(any(), any(), any()) } returns
            Result.success(SignUpOutcome.Authenticated)
        val viewModel = validSignUpViewModel()

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.signedIn)
        coVerify(exactly = 0) { repository.ensureUserRow() }
    }

    @Test
    fun `confirmation resend waits for cooldown and rejects double send`() = runTest(dispatcher) {
        coEvery { repository.signUpWithEmail(any(), any(), any()) } returns
            Result.success(SignUpOutcome.ConfirmationRequired("person@example.com"))
        coEvery { repository.resendSignUpConfirmation("person@example.com") } returns
            Result.success(Unit)
        val viewModel = validSignUpViewModel()

        viewModel.submit()
        runCurrent()

        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)
        assertFalse(viewModel.uiState.value.canResendConfirmation)
        viewModel.resendConfirmation()
        runCurrent()
        coVerify(exactly = 0) { repository.resendSignUpConfirmation(any()) }

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(viewModel.uiState.value.canResendConfirmation)

        viewModel.resendConfirmation()
        viewModel.resendConfirmation()
        assertTrue(viewModel.uiState.value.isResendingConfirmation)
        runCurrent()

        coVerify(exactly = 1) {
            repository.resendSignUpConfirmation("person@example.com")
        }
        assertFalse(viewModel.uiState.value.isResendingConfirmation)
        assertEquals(ConfirmationResendFeedback.Sent, viewModel.uiState.value.resendFeedback)
        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `failed confirmation resend exposes error and keeps rate limit`() = runTest(dispatcher) {
        coEvery { repository.signUpWithEmail(any(), any(), any()) } returns
            Result.success(SignUpOutcome.ConfirmationRequired("person@example.com"))
        coEvery { repository.resendSignUpConfirmation(any()) } returns
            Result.failure(IllegalStateException("network"))
        val viewModel = validSignUpViewModel()
        viewModel.submit()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        viewModel.resendConfirmation()
        viewModel.resendConfirmation()
        runCurrent()

        coVerify(exactly = 1) { repository.resendSignUpConfirmation("person@example.com") }
        assertEquals(ConfirmationResendFeedback.Failed, viewModel.uiState.value.resendFeedback)
        assertFalse(viewModel.uiState.value.isResendingConfirmation)
        assertFalse(viewModel.uiState.value.canResendConfirmation)
        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(viewModel.uiState.value.canResendConfirmation)
    }

    private fun validSignUpViewModel() = EmailAuthViewModel(repository).apply {
        setMode(AuthMode.SignUp)
        onNameChange("Ada")
        onEmailChange("person@example.com")
        onPasswordChange("password123")
    }
}
