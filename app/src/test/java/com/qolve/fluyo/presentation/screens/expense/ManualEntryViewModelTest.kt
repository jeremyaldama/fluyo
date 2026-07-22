package com.qolve.fluyo.presentation.screens.expense

import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.usecase.RegisterExpenseUseCase
import com.qolve.fluyo.notifications.AchievementScheduler
import com.qolve.fluyo.presentation.events.AppEvents
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uncertain response and restored ViewModel reuse request id`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val register = mockk<RegisterExpenseUseCase>()
        val requestIds = mutableListOf<String>()
        coEvery {
            register.invoke(any(), any(), any(), any(), any(), any(), any(), capture(requestIds))
        } returnsMany listOf(
            Result.failure(IllegalStateException("response lost")),
            Result.success(savedExpense()),
        )

        val first = viewModel(savedState, register)
        fillValidForm(first)
        first.save()
        advanceUntilIdle()
        assertFalse(first.state.value.savedOk)

        val restored = viewModel(savedState, register)
        fillValidForm(restored)
        restored.save()
        advanceUntilIdle()

        assertTrue(restored.state.value.savedOk)
        assertEquals(2, requestIds.size)
        assertEquals(requestIds.first(), requestIds.last())
    }

    @Test
    fun `changed restored form reconciles committed request before creating again`() =
        runTest(dispatcher) {
            val savedState = SavedStateHandle()
            val register = mockk<RegisterExpenseUseCase>()
            coEvery {
                register.invoke(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Result.failure(IllegalStateException("response lost"))
            val repository = mockk<ExpenseRepository>(relaxed = true)
            coEvery { repository.findCreatedByRequestId(any()) } returns
                Result.success(savedExpense())

            val first = viewModel(savedState, register, expenseRepository = repository)
            fillValidForm(first)
            first.save()
            advanceUntilIdle()

            val restored = viewModel(savedState, register, expenseRepository = repository)
            fillValidForm(restored)
            restored.onDescriptionChange("Descripción reextraída")
            restored.save()
            advanceUntilIdle()

            assertTrue(restored.state.value.savedOk)
            coVerify(exactly = 1) {
                register.invoke(any(), any(), any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) { repository.findCreatedByRequestId(any()) }
        }

    @Test
    fun `double submit while saving invokes creation once`() = runTest(dispatcher) {
        val register = mockk<RegisterExpenseUseCase>()
        coEvery {
            register.invoke(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(savedExpense())
        val scheduler = mockk<AchievementScheduler>(relaxed = true)
        val viewModel = viewModel(
            savedState = SavedStateHandle(),
            register = register,
            achievementScheduler = scheduler,
        )
        fillValidForm(viewModel)

        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.savedOk)
        coVerify(exactly = 1) {
            register.invoke(any(), any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 1) { scheduler.reconcileExpense() }
    }

    @Test
    fun `edit remains blocked and explains missing or unowned expense`() = runTest(dispatcher) {
        val repository = mockk<ExpenseRepository>(relaxed = true)
        coEvery { repository.getById("missing") } returns Result.success(null)
        val savedState = SavedStateHandle(mapOf("expenseId" to "missing"))
        val viewModel = viewModel(
            savedState = savedState,
            register = mockk(relaxed = true),
            expenseRepository = repository,
        )

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isEditReady)
        assertFalse(viewModel.state.value.canSave)
        assertTrue(viewModel.state.value.loadErrorMessage.orEmpty().contains("no tienes acceso"))
        viewModel.save()
        coVerify(exactly = 0) { repository.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `resume does not duplicate an active edit load and retry replaces it`() = runTest(dispatcher) {
        val repository = mockk<ExpenseRepository>(relaxed = true)
        val firstResponse = CompletableDeferred<Result<Expense?>>()
        var calls = 0
        coEvery { repository.getById("expense-1") } coAnswers {
            calls += 1
            if (calls == 1) firstResponse.await() else Result.success(savedExpense())
        }
        val viewModel = viewModel(
            savedState = SavedStateHandle(mapOf("expenseId" to "expense-1")),
            register = mockk(relaxed = true),
            expenseRepository = repository,
        )
        runCurrent()

        viewModel.onResume()
        runCurrent()
        coVerify(exactly = 1) { repository.getById("expense-1") }

        viewModel.retryEditLoad()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getById("expense-1") }
        assertTrue(viewModel.state.value.isEditReady)
        assertEquals("12.34", viewModel.state.value.amountInput)
    }

    @Test
    fun `edit cannot mutate or save before lookup completes`() = runTest(dispatcher) {
        val repository = mockk<ExpenseRepository>(relaxed = true)
        val response = CompletableDeferred<Result<Expense?>>()
        coEvery { repository.getById("expense-1") } coAnswers { response.await() }
        val viewModel = viewModel(
            savedState = SavedStateHandle(mapOf("expenseId" to "expense-1")),
            register = mockk(relaxed = true),
            expenseRepository = repository,
        )
        runCurrent()

        viewModel.onAmountChange("99")
        viewModel.onCategorySelect("category-1")
        viewModel.save()

        assertTrue(viewModel.state.value.amountInput.isEmpty())
        assertFalse(viewModel.state.value.canSave)
        coVerify(exactly = 0) { repository.update(any(), any(), any(), any(), any()) }
        response.complete(Result.success(savedExpense()))
        advanceUntilIdle()
    }

    private fun viewModel(
        savedState: SavedStateHandle,
        register: RegisterExpenseUseCase,
        expenseRepository: ExpenseRepository = mockk(relaxed = true),
        achievementScheduler: AchievementScheduler = mockk(relaxed = true),
    ): ManualEntryViewModel {
        val categoryRepository = mockk<CategoryRepository>()
        every { categoryRepository.observeCategories() } returns MutableStateFlow(listOf(category()))
        coEvery { categoryRepository.refresh() } returns Result.success(Unit)
        val boundary = SessionEpoch()
        return ManualEntryViewModel(
            categoryRepository = categoryRepository,
            registerExpense = register,
            expenseRepository = expenseRepository,
            appEvents = AppEvents(boundary),
            achievementScheduler = achievementScheduler,
            sessionBoundary = boundary,
            savedStateHandle = savedState,
        )
    }

    private fun fillValidForm(viewModel: ManualEntryViewModel) {
        viewModel.onAmountChange("12.34")
        viewModel.onDescriptionChange("Taxi")
        viewModel.onCategorySelect("category-1")
    }

    private fun category() = Category(
        id = "category-1",
        name = "Transporte",
        icon = "bus",
        color = "#000000",
        isDefault = true,
        displayOrder = 1,
    )

    private fun savedExpense() = Expense(
        id = "expense-1",
        amount = MoneyAmount.ofCents(1_234L),
        categoryId = "category-1",
        description = "Taxi",
        expenseDate = LocalDate.of(2026, 7, 22),
        source = ExpenseSource.MANUAL,
        recipient = null,
        imageUrl = null,
        createdAt = Instant.EPOCH,
    )
}
