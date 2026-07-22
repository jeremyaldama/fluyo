package com.qolve.fluyo.notifications

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementSchedulerTest {

    @Test
    fun `one-shot actions have distinct unique names and stable wire values`() {
        val actions = listOf(
            AchievementWorkAction.EXPENSE,
            AchievementWorkAction.GOAL_COMPLETED,
            AchievementWorkAction.DEPOSIT,
        )

        assertEquals(actions.size, actions.map { it.uniqueWorkName }.toSet().size)
        assertEquals(actions.size, actions.map { it.wire }.toSet().size)
        actions.forEach { action ->
            assertEquals(action, AchievementWorkAction.fromWire(action.wire))
            assertNotEquals(ACHIEVEMENT_PERIODIC_WORK_NAME, action.uniqueWorkName)
        }
        assertEquals(null, AchievementWorkAction.fromWire("unexpected"))
    }

    @Test
    fun `one-shot request requires network and carries action plus cancellation tags`() {
        val request = achievementOneShotRequest(AchievementWorkAction.DEPOSIT)

        assertEquals(
            AchievementWorkAction.DEPOSIT.wire,
            request.workSpec.input.getString(ACHIEVEMENT_ACTION_KEY),
        )
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(ACHIEVEMENT_WORK_TAG in request.tags)
        assertTrue(AchievementWorkAction.DEPOSIT.uniqueWorkName in request.tags)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `closed month cannot accidentally be enqueued as one-shot`() {
        achievementOneShotRequest(AchievementWorkAction.CLOSED_MONTH)
    }
}
