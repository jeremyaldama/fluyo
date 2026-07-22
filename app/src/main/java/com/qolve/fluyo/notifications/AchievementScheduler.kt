package com.qolve.fluyo.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.qolve.fluyo.domain.model.BadgeType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules idempotent achievement catch-up even when nudges are disabled. */
@Singleton
class AchievementScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<AchievementWorker>(
            repeatInterval = Duration.ofHours(24),
            flexTimeInterval = Duration.ofHours(2),
        )
            .setInputData(achievementInputData(AchievementWorkAction.CLOSED_MONTH))
            .setConstraints(achievementNetworkConstraints())
            .addTag(ACHIEVEMENT_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ACHIEVEMENT_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Coalesces bursts safely: each worker recomputes every applicable rule from the server. */
    fun reconcileExpense() = enqueue(AchievementWorkAction.EXPENSE)

    fun reconcileGoalCompletion() = enqueue(AchievementWorkAction.GOAL_COMPLETED)

    fun reconcileDeposit() = enqueue(AchievementWorkAction.DEPOSIT)

    private fun enqueue(action: AchievementWorkAction) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            action.uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            achievementOneShotRequest(action),
        )
    }

    fun cancel() {
        // The shared tag covers the periodic catch-up and every one-shot action.
        WorkManager.getInstance(context).cancelAllWorkByTag(ACHIEVEMENT_WORK_TAG)
        val notifications = NotificationManagerCompat.from(context)
        BadgeType.entries.forEach { type ->
            notifications.cancel(BadgeNotifier.BADGE_NOTIFICATION_BASE_ID + type.ordinal)
        }
    }
}

internal const val ACHIEVEMENT_ACTION_KEY = "achievement_action"
internal const val ACHIEVEMENT_WORK_TAG = "fluyo_achievement"
// Keep the historical unique name so app upgrades update rather than duplicate periodic work.
internal const val ACHIEVEMENT_PERIODIC_WORK_NAME = "fluyo_achievement_worker"

internal enum class AchievementWorkAction(
    val wire: String,
    val uniqueWorkName: String,
) {
    EXPENSE("expense", "fluyo_achievement_expense"),
    GOAL_COMPLETED("goal", "fluyo_achievement_goal"),
    DEPOSIT("deposit", "fluyo_achievement_deposit"),
    // Keep the legacy wire value so already persisted periodic input remains compatible.
    CLOSED_MONTH("closed_month", ACHIEVEMENT_PERIODIC_WORK_NAME),
    ;

    companion object {
        fun fromWire(value: String): AchievementWorkAction? = entries.firstOrNull { it.wire == value }
    }
}

internal fun achievementInputData(action: AchievementWorkAction) =
    workDataOf(ACHIEVEMENT_ACTION_KEY to action.wire)

internal fun achievementNetworkConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

internal fun achievementOneShotRequest(action: AchievementWorkAction): OneTimeWorkRequest {
    require(action != AchievementWorkAction.CLOSED_MONTH) {
        "Closed-month reconciliation is periodic, not one-shot"
    }
    return OneTimeWorkRequestBuilder<AchievementWorker>()
        .setInputData(achievementInputData(action))
        .setConstraints(achievementNetworkConstraints())
        .addTag(ACHIEVEMENT_WORK_TAG)
        .addTag(action.uniqueWorkName)
        .build()
}
