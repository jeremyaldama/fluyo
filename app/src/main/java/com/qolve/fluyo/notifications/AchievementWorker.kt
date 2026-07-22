package com.qolve.fluyo.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qolve.fluyo.domain.usecase.BadgeEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Evaluates catch-up achievements independently from the user's nudge preference. */
@HiltWorker
class AchievementWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val badgeEngine: BadgeEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            when (requestedAction()) {
                AchievementWorkAction.EXPENSE -> badgeEngine.checkAfterExpense()
                AchievementWorkAction.GOAL_COMPLETED -> badgeEngine.checkAfterGoalCompleted()
                AchievementWorkAction.DEPOSIT -> badgeEngine.checkAfterDeposit()
                AchievementWorkAction.CLOSED_MONTH -> badgeEngine.checkAllBadges()
                null -> return Result.failure()
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    /** Missing input keeps already-enqueued periodic work from older app versions valid. */
    private fun requestedAction(): AchievementWorkAction? {
        val wire = inputData.getString(ACHIEVEMENT_ACTION_KEY)
            ?: return AchievementWorkAction.CLOSED_MONTH
        return AchievementWorkAction.fromWire(wire)
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
