package com.qolve.fluyo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Unit tests for the pure domain logic on [Goal]. */
class GoalTest {

    private fun goal(target: Long, current: Long, status: GoalStatus = GoalStatus.ACTIVE) =
        Goal(
            id = "g1",
            name = "Audífonos",
            targetAmount = MoneyAmount.ofCents(target * 100L),
            currentAmount = MoneyAmount.ofCents(current * 100L),
            deadline = null,
            status = status,
            createdAt = Instant.EPOCH,
            completedAt = null,
        )

    @Test
    fun `progress is the current over target ratio`() {
        assertEquals(0.25f, goal(target = 200, current = 50).progress, 0.001f)
    }

    @Test
    fun `progress is clamped to 1 when over-funded`() {
        assertEquals(1f, goal(target = 200, current = 300).progress, 0.001f)
    }

    @Test
    fun `progress is zero when target is non-positive`() {
        assertEquals(0f, goal(target = 0, current = 50).progress, 0.001f)
    }

    @Test
    fun `remaining is target minus current, never negative`() {
        assertEquals(MoneyAmount.ofCents(15_000L), goal(target = 200, current = 50).remaining)
        assertEquals(MoneyAmount.ZERO, goal(target = 200, current = 300).remaining)
    }

    @Test
    fun `isCompleted reflects the status`() {
        assertTrue(goal(200, 200, GoalStatus.COMPLETED).isCompleted)
        assertFalse(goal(200, 50, GoalStatus.ACTIVE).isCompleted)
    }
}
