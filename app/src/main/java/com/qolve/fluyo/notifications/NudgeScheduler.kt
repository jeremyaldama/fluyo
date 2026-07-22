package com.qolve.fluyo.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily nudge worker so it fires roughly at the user's chosen
 * hour. We rely on WorkManager's periodic semantics (24h) rather than exact
 * alarms — fine for "max 1 nudge/day" UX and avoids the SCHEDULE_EXACT_ALARM
 * permission on Android 14+.
 */
@Singleton
class NudgeScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun schedule(hourOfDay: Int) {
        val initialDelay = computeInitialDelay(hourOfDay.coerceIn(0, 23))

        val request = PeriodicWorkRequestBuilder<NudgeWorker>(
            Duration.ofHours(24),
            Duration.ofMinutes(30),
        )
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NudgeWorker.NUDGE_NOTIFICATION_ID)
    }

    private fun computeInitialDelay(hourOfDay: Int): Duration {
        val now = LocalDateTime.now()
        val today = LocalDate.now().atTime(LocalTime.of(hourOfDay, 0))
        val target = if (today.isAfter(now)) today else today.plusDays(1)
        val zone = ZoneId.systemDefault()
        val millis = target.atZone(zone).toInstant().toEpochMilli() -
            now.atZone(zone).toInstant().toEpochMilli()
        return Duration.ofMillis(millis.coerceAtLeast(0L))
    }

    companion object {
        const val WORK_NAME = "fluyo_nudge_worker"
        const val WORK_TAG = "fluyo_nudge"
    }
}
