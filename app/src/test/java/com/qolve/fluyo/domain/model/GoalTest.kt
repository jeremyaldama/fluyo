package com.qolve.fluyo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Unit tests for the pure domain logic on [Goal]. */
class GoalTest {

    private fun goal(target: Double, current: Double, status: GoalStatus = GoalStatus.ACTIVE) =
        Goal(
            id = "g1",
            name = "Audífonos",
            targetAmount = target,
            currentAmount = current,
            deadline = null,
            status = status,
            createdAt = Instant.EPOCH,
            completedAt = null,
        )

    @Test
    fun `progress is the current over target ratio`() {
        assertEquals(0.25f, goal(target = 200.0, current = 50.0).progress, 0.001f)
    }

    @Test
    fun `progress is clamped to 1 when over-funded`() {
        assertEquals(1f, goal(target = 200.0, current = 300.0).progress, 0.001f)
    }

    @Test
    fun `progress is zero when target is non-positive`() {
        assertEquals(0f, goal(target = 0.0, current = 50.0).progress, 0.001f)
    }

    @Test
    fun `remaining is target minus current, never negative`() {
        assertEquals(150.0, goal(target = 200.0, current = 50.0).remaining, 0.001)
        assertEquals(0.0, goal(target = 200.0, current = 300.0).remaining, 0.001)
    }

    @Test
    fun `isCompleted reflects the status`() {
        assertTrue(goal(200.0, 200.0, GoalStatus.COMPLETED).isCompleted)
        assertFalse(goal(200.0, 50.0, GoalStatus.ACTIVE).isCompleted)
    }
}
