package com.qolve.fluyo.presentation.screens.goals

import androidx.lifecycle.SavedStateHandle
import com.qolve.fluyo.domain.model.MoneyAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PendingDepositRequestStoreTest {

    @Test
    fun `retry of the same goal and cents reuses request id`() {
        var sequence = 0
        val savedState = SavedStateHandle()
        val store = PendingDepositRequestStore(savedState) { "request-${++sequence}" }
        val amount = MoneyAmount.ofCents(1_050L)

        val first = store.getOrCreate("goal-1", amount)
        val retry = store.getOrCreate("goal-1", amount)

        assertEquals(first, retry)
        assertEquals(1, sequence)
        // Reconstructing after process state restoration keeps the same logical request.
        assertEquals(
            first,
            PendingDepositRequestStore(savedState) { "unexpected" }.getOrCreate("goal-1", amount),
        )
    }

    @Test
    fun `different amount cannot replace an uncertain deposit`() {
        var sequence = 0
        val store = PendingDepositRequestStore(SavedStateHandle()) { "request-${++sequence}" }

        val first = store.getOrCreate("goal-1", MoneyAmount.ofCents(1_000L))
        assertThrows(IllegalStateException::class.java) {
            store.getOrCreate("goal-1", MoneyAmount.ofCents(1_001L))
        }
        assertEquals(first, store.pending()?.requestId)
    }

    @Test
    fun `only confirmed success releases the idempotency key`() {
        var sequence = 0
        val store = PendingDepositRequestStore(SavedStateHandle()) { "request-${++sequence}" }
        val amount = MoneyAmount.ofCents(500L)

        val completed = store.getOrCreate("goal-1", amount)
        store.complete(completed)
        val next = store.getOrCreate("goal-1", amount)

        assertNotEquals(completed, next)
        assertEquals(next, store.pending()?.requestId)
    }
}
