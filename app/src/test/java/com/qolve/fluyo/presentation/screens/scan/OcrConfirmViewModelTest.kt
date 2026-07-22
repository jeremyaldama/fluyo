package com.qolve.fluyo.presentation.screens.scan

import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.data.local.SensitiveCacheCleaner
import com.qolve.fluyo.data.ocr.OcrService
import com.qolve.fluyo.data.ocr.SecureOcrImageImporter
import com.qolve.fluyo.data.ocr.YapeParser
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.DetectedField
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.ParsedReceipt
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.time.FluyoTime
import com.qolve.fluyo.domain.usecase.RegisterExpenseUseCase
import com.qolve.fluyo.notifications.AchievementScheduler
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.presentation.util.MIN_EXPENSE_DATE
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class OcrConfirmViewModelTest {
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
        val scheduler = mockk<AchievementScheduler>(relaxed = true)

        val first = viewModel(savedState, register, scheduler)
        fillValidForm(first)
        first.save()
        advanceUntilIdle()
        assertFalse(first.state.value.savedOk)

        val restored = viewModel(savedState, register, scheduler)
        fillValidForm(restored)
        restored.save()
        advanceUntilIdle()

        assertTrue(restored.state.value.savedOk)
        assertEquals(2, requestIds.size)
        assertEquals(requestIds.first(), requestIds.last())
        verify(exactly = 1) { scheduler.reconcileExpense() }
    }

    @Test
    fun `double submit creates once and marks success before scheduling reconciliation`() =
        runTest(dispatcher) {
            val register = mockk<RegisterExpenseUseCase>()
            coEvery {
                register.invoke(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Result.success(savedExpense())
            val scheduler = mockk<AchievementScheduler>()
            lateinit var viewModel: OcrConfirmViewModel
            every { scheduler.reconcileExpense() } answers {
                assertTrue(viewModel.state.value.savedOk)
            }
            viewModel = viewModel(SavedStateHandle(), register, scheduler)
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
    fun `changed OCR result reconciles prior commit instead of duplicating expense`() =
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
            restored.onRecipientChange("OCR ligeramente distinto")
            restored.save()
            advanceUntilIdle()

            assertTrue(restored.state.value.savedOk)
            coVerify(exactly = 1) {
                register.invoke(any(), any(), any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 1) { repository.findCreatedByRequestId(any()) }
        }

    @Test
    fun `can save accepts only expense dates from 2000 through today`() {
        val today = FluyoTime.today()
        val validState = OcrConfirmUiState(
            isProcessing = false,
            amountInput = "12.34",
            selectedCategoryId = "category-1",
        )

        assertTrue(validState.copy(date = MIN_EXPENSE_DATE).canSave)
        assertTrue(validState.copy(date = today).canSave)
        assertFalse(validState.copy(date = MIN_EXPENSE_DATE.minusDays(1)).canSave)
        assertFalse(validState.copy(date = today.plusDays(1)).canSave)
    }

    @Test
    fun `valid parsed date is applied and remains marked as detected`() {
        val viewModel = viewModel()
        val validDate = FluyoTime.today().minusDays(1)

        viewModel.applyParsed(
            ParsedReceipt(
                amount = MoneyAmount.ofCents(1_234L),
                date = validDate,
                detected = setOf(DetectedField.AMOUNT, DetectedField.DATE),
            ),
        )

        assertEquals(validDate, viewModel.state.value.date)
        assertTrue(DetectedField.DATE in viewModel.state.value.autoDetected)
    }

    @Test
    fun `invalid parsed dates are ignored and are not marked as detected`() {
        val viewModel = viewModel()
        val initialDate = viewModel.state.value.date

        listOf(MIN_EXPENSE_DATE.minusDays(1), FluyoTime.today().plusDays(1)).forEach { invalid ->
            viewModel.applyParsed(
                ParsedReceipt(
                    amount = MoneyAmount.ofCents(1_234L),
                    date = invalid,
                    detected = setOf(DetectedField.AMOUNT, DetectedField.DATE),
                ),
            )

            assertEquals(initialDate, viewModel.state.value.date)
            assertFalse(DetectedField.DATE in viewModel.state.value.autoDetected)
            assertTrue(DetectedField.AMOUNT in viewModel.state.value.autoDetected)
        }
    }

    @Test
    fun `editing date validates range and clears detected marker`() {
        val viewModel = viewModel()
        val parsedDate = FluyoTime.today().minusDays(1)
        viewModel.applyParsed(
            ParsedReceipt(
                amount = MoneyAmount.ofCents(1_234L),
                date = parsedDate,
                detected = setOf(DetectedField.DATE),
            ),
        )
        val editedDate = parsedDate.minusDays(1)

        viewModel.onDateChange(editedDate)

        assertEquals(editedDate, viewModel.state.value.date)
        assertFalse(DetectedField.DATE in viewModel.state.value.autoDetected)
        assertEquals(null, viewModel.state.value.errorMessage)

        viewModel.onDateChange(FluyoTime.today().plusDays(1))

        assertEquals(editedDate, viewModel.state.value.date)
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `category refresh failure is visible and retry unblocks the form`() =
        runTest(dispatcher) {
            val categoryFlow = MutableStateFlow(emptyList<Category>())
            val categories = mockk<CategoryRepository>()
            every { categories.observeCategories() } returns categoryFlow
            var refreshCalls = 0
            coEvery { categories.refresh() } coAnswers {
                refreshCalls += 1
                if (refreshCalls == 1) {
                    Result.failure(IllegalStateException("offline"))
                } else {
                    categoryFlow.value = listOf(category())
                    Result.success(Unit)
                }
            }
            val viewModel = viewModel(categoryRepository = categories)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoadingCategories)
            assertTrue(viewModel.state.value.categoryLoadError != null)
            assertTrue(viewModel.state.value.categories.isEmpty())

            viewModel.retryCategories()
            advanceUntilIdle()

            assertEquals(2, refreshCalls)
            assertEquals("category-1", viewModel.state.value.selectedCategoryId)
            assertEquals(null, viewModel.state.value.categoryLoadError)
            assertFalse(viewModel.state.value.isLoadingCategories)
        }

    private fun viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        register: RegisterExpenseUseCase = mockk(relaxed = true),
        scheduler: AchievementScheduler = mockk(relaxed = true),
        expenseRepository: ExpenseRepository = mockk(relaxed = true),
        categoryRepository: CategoryRepository? = null,
    ): OcrConfirmViewModel {
        val categories = categoryRepository ?: mockk<CategoryRepository>().also { repository ->
            coEvery { repository.refresh() } returns Result.success(Unit)
            every { repository.observeCategories() } returns MutableStateFlow(listOf(category()))
        }
        val boundary = SessionEpoch()
        return OcrConfirmViewModel(
            savedStateHandle = savedState,
            imageImporter = mockk<SecureOcrImageImporter>(relaxed = true),
            ocrService = mockk<OcrService>(relaxed = true),
            yapeParser = mockk<YapeParser>(relaxed = true),
            categoryRepository = categories,
            expenseRepository = expenseRepository,
            registerExpense = register,
            appEvents = AppEvents(boundary),
            achievementScheduler = scheduler,
            sensitiveCacheCleaner = mockk<SensitiveCacheCleaner>(relaxed = true),
            sessionBoundary = boundary,
        )
    }

    private fun fillValidForm(viewModel: OcrConfirmViewModel) {
        viewModel.onAmountChange("12.34")
        viewModel.onDescriptionChange(" Almuerzo ")
        viewModel.onRecipientChange(" Restaurante ")
        viewModel.onCategorySelect("category-1")
    }

    private fun category() = Category(
        id = "category-1",
        name = "Comida",
        icon = "restaurant",
        color = "#000000",
        isDefault = true,
        displayOrder = 1,
    )

    private fun savedExpense() = Expense(
        id = "expense-1",
        amount = MoneyAmount.ofCents(1_234L),
        categoryId = "category-1",
        description = "Almuerzo",
        expenseDate = LocalDate.of(2026, 7, 22),
        source = ExpenseSource.OCR,
        recipient = "Restaurante",
        imageUrl = null,
        createdAt = Instant.EPOCH,
    )
}
