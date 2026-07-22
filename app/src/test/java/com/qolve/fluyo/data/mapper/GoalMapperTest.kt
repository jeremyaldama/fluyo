package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.GoalDepositRpcParams
import com.qolve.fluyo.data.dto.GoalDepositRpcResultDto
import com.qolve.fluyo.data.dto.GoalDto
import com.qolve.fluyo.domain.model.GoalStatus
import com.qolve.fluyo.domain.model.MoneyAmount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMapperTest {

    @Test
    fun `goal view deposit count reaches the domain model`() {
        val goal = GoalDto(
            id = "goal-1",
            userId = "user-1",
            name = "Laptop",
            targetAmount = 1_000.0,
            currentAmount = 250.0,
            status = "active",
            createdAt = "2026-07-22T00:00:00Z",
            depositCount = 3,
        ).toDomain()

        assertEquals(3, goal.depositCount)
        assertEquals(GoalStatus.ACTIVE, goal.status)
    }

    @Test
    fun `deposit RPC result preserves completion transition and count`() {
        val outcome = GoalDepositRpcResultDto(
            id = "goal-1",
            userId = "user-1",
            name = "Laptop",
            targetAmount = 1_000.0,
            currentAmount = 1_000.0,
            deadline = "2026-12-31",
            status = "completed",
            createdAt = "2026-07-01T12:30:00Z",
            completedAt = "2026-07-22T02:00:00Z",
            depositCount = 4,
            justCompleted = true,
        ).toOutcome()

        assertTrue(outcome.justCompleted)
        assertEquals(GoalStatus.COMPLETED, outcome.goal.status)
        assertEquals(4, outcome.goal.depositCount)
        assertEquals(MoneyAmount.ofCents(100_000L), outcome.goal.currentAmount)
    }

    @Test
    fun `deposit RPC parameter names match the PostgreSQL signature`() {
        val encoded = Json.encodeToString(
            GoalDepositRpcParams(
                goalId = "goal-1",
                amount = 25.5,
                requestId = "request-1",
            ),
        )

        assertTrue(encoded.contains("\"p_goal_id\":\"goal-1\""))
        assertTrue(encoded.contains("\"p_amount\":25.5"))
        assertTrue(encoded.contains("\"p_request_id\":\"request-1\""))
        assertFalse(encoded.contains("\"goalId\""))
    }
}
