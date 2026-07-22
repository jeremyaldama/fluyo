package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.domain.model.Expense
import com.qolve.fluyo.domain.model.ExpenseSource
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseExpenseRepositoryTest {

    @Test
    fun `observeRecentExpenses applies requested limit to every snapshot`() = runTest {
        val repository = repository()
        repository.publishForTest((1..4).map(::expense))

        val observed = repository.observeRecentExpenses(limit = 2).first()

        assertEquals(listOf("expense-1", "expense-2"), observed.map(Expense::id))
    }

    @Test
    fun `observeRecentExpenses treats a negative limit as zero`() = runTest {
        val repository = repository()
        repository.publishForTest(listOf(expense(1)))

        assertEquals(emptyList<Expense>(), repository.observeRecentExpenses(limit = -1).first())
    }

    private fun repository() = SupabaseExpenseRepository(
        client = mockk<SupabaseClient>(),
        authRepository = mockk<AuthRepository>(),
        sessionBoundary = SessionEpoch(),
    )

    private fun expense(index: Int) = Expense(
        id = "expense-$index",
        amount = MoneyAmount.ofCents(index * 100L),
        categoryId = null,
        description = null,
        expenseDate = LocalDate.of(2026, 7, 22).minusDays(index.toLong()),
        source = ExpenseSource.MANUAL,
        recipient = null,
        imageUrl = null,
        createdAt = Instant.EPOCH.plusSeconds(index.toLong()),
    )

    @Suppress("UNCHECKED_CAST")
    private fun SupabaseExpenseRepository.publishForTest(expenses: List<Expense>) {
        val field = javaClass.getDeclaredField("expensesState").apply { isAccessible = true }
        val state = field.get(this) as MutableStateFlow<List<Expense>>
        state.value = expenses
    }
}
