package com.qolve.fluyo.presentation.screens.profile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.WhatsAppLinkRepository
import com.qolve.fluyo.notifications.NudgeOneShot
import com.qolve.fluyo.notifications.NudgeScheduler
import com.qolve.fluyo.presentation.util.CsvExportArtifact
import com.qolve.fluyo.presentation.util.CsvExporter
import com.qolve.fluyo.presentation.util.CurrencyState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `export refreshes categories and uses authoritative profile currency`() =
        runTest(dispatcher) {
            val auth = mockk<AuthRepository>()
            coEvery { auth.currentUser() } returns Result.success(user("USD"))
            coEvery { auth.hasMonetaryActivity() } returns Result.success(false)
            val badges = mockk<BadgeRepository>()
            every { badges.observeBadges() } returns MutableStateFlow(emptyList<Badge>())
            coEvery { badges.refresh() } returns Result.success(Unit)
            val expenses = mockk<ExpenseRepository>()
            coEvery { expenses.currentStreak() } returns Result.success(0)
            coEvery { expenses.loadByDateRange(any(), any()) } returns Result.success(emptyList())
            val categories = mockk<CategoryRepository>()
            val category = Category("category-1", "Comida", "restaurant", "#000000", true, 1)
            every { categories.observeCategories() } returns MutableStateFlow(listOf(category))
            coEvery { categories.refresh() } returns Result.success(Unit)
            val artifact = mockk<CsvExportArtifact>()
            every { artifact.uri } returns mockk<Uri>(relaxed = true)
            val exporter = mockk<CsvExporter>()
            coEvery { exporter.export(any(), any(), any(), any()) } coAnswers {
                arg<() -> Unit>(3).invoke()
                artifact
            }

            val viewModel = ProfileViewModel(
                authRepository = auth,
                badgeRepository = badges,
                expenseRepository = expenses,
                categoryRepository = categories,
                budgetExtraRepository = mockk<BudgetExtraRepository>(relaxed = true),
                whatsAppLinkRepository = mockk<WhatsAppLinkRepository>(relaxed = true),
                nudgeScheduler = mockk<NudgeScheduler>(relaxed = true),
                nudgeOneShot = mockk<NudgeOneShot>(relaxed = true),
                currencyState = CurrencyState(),
                csvExporter = exporter,
                sessionBoundary = SessionEpoch(),
                savedStateHandle = SavedStateHandle(),
            )
            advanceUntilIdle()

            viewModel.exportCsv()
            advanceUntilIdle()

            coVerify(exactly = 1) { categories.refresh() }
            coVerify(exactly = 1) {
                expenses.loadByDateRange(LocalDate.of(2000, 1, 1), any())
            }
            coVerify(exactly = 1) {
                exporter.export(emptyList(), mapOf("category-1" to "Comida"), "USD", any())
            }
        }

    @Test
    fun `export fails closed when authoritative categories cannot refresh`() =
        runTest(dispatcher) {
            val auth = mockk<AuthRepository>()
            coEvery { auth.currentUser() } returns Result.success(user("EUR"))
            coEvery { auth.hasMonetaryActivity() } returns Result.success(false)
            val badges = mockk<BadgeRepository>()
            every { badges.observeBadges() } returns MutableStateFlow(emptyList())
            coEvery { badges.refresh() } returns Result.success(Unit)
            val expenses = mockk<ExpenseRepository>(relaxed = true)
            coEvery { expenses.currentStreak() } returns Result.success(0)
            val categories = mockk<CategoryRepository>()
            every { categories.observeCategories() } returns MutableStateFlow(emptyList())
            coEvery { categories.refresh() } returns Result.failure(IllegalStateException("offline"))
            val exporter = mockk<CsvExporter>(relaxed = true)

            val viewModel = ProfileViewModel(
                authRepository = auth,
                badgeRepository = badges,
                expenseRepository = expenses,
                categoryRepository = categories,
                budgetExtraRepository = mockk(relaxed = true),
                whatsAppLinkRepository = mockk(relaxed = true),
                nudgeScheduler = mockk(relaxed = true),
                nudgeOneShot = mockk(relaxed = true),
                currencyState = CurrencyState(),
                csvExporter = exporter,
                sessionBoundary = SessionEpoch(),
                savedStateHandle = SavedStateHandle(),
            )
            advanceUntilIdle()

            viewModel.exportCsv()
            advanceUntilIdle()

            coVerify(exactly = 1) { categories.refresh() }
            coVerify(exactly = 0) { expenses.loadByDateRange(any(), any()) }
            coVerify(exactly = 0) { exporter.export(any(), any(), any(), any()) }
        }

    private fun user(currency: String) = User(
        id = "user-1",
        authId = "auth-1",
        email = "user@example.test",
        displayName = "Ada",
        monthlyBudget = MoneyAmount.ZERO,
        currency = currency,
        level = 1,
        totalPoints = 0,
    )
}
