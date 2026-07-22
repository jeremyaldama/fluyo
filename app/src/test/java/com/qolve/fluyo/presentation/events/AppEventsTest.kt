package com.qolve.fluyo.presentation.events

import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.data.SessionEpoch

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppEventsTest {

    @Test
    fun `event emitted before collector is retained`() = runTest {
        val boundary = SessionEpoch()
        val events = AppEvents(boundary)
        val expected = AppEvent.ExpenseSaved(MoneyAmount.ofCents(1_550L), AppEvent.ExpenseSaved.Source.OCR)

        events.emit(expected, boundary.snapshot())

        assertEquals(expected, events.events.first())
    }

    @Test
    fun `queued events preserve order`() = runTest {
        val boundary = SessionEpoch()
        val events = AppEvents(boundary)
        val first = AppEvent.BadgeUnlocked("streak_7")
        val second = AppEvent.ExpenseSaved(MoneyAmount.ofCents(1_000L), AppEvent.ExpenseSaved.Source.MANUAL)

        events.emit(first, boundary.snapshot())
        events.emit(second, boundary.snapshot())

        assertEquals(first, events.events.first())
        assertEquals(second, events.events.first())
    }

    @Test
    fun `late event from previous identity is rejected`() = runTest {
        val boundary = SessionEpoch()
        val events = AppEvents(boundary)
        val staleEpoch = boundary.snapshot()

        boundary.beginTransition()
        events.clearForSignOut()
        boundary.completeTransition()

        val accepted = events.emit(AppEvent.BadgeUnlocked("streak_7"), staleEpoch)

        assertEquals(false, accepted)
    }
}
