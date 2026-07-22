package com.qolve.fluyo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qolve.fluyo.MainActivity
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.repository.NudgeHistoryRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.usecase.ComputeNudgeUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class NudgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val computeNudge: ComputeNudgeUseCase,
    private val nudgeHistory: NudgeHistoryRepository,
    private val sessionBoundary: SessionBoundary,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val decision = try {
            computeNudge()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } ?: return Result.success()
        val nudge = decision.content
        val sessionEpoch = decision.sessionEpoch
        if (!sessionBoundary.isCurrent(sessionEpoch)) return Result.success()

        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!canPost) return Result.success()

        val claimed = try {
            nudgeHistory.claimToday(sessionEpoch)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
        if (!claimed) return Result.success()

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, FluyoChannels.NUDGES_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(nudge.title)
            .setContentText(nudge.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Atomic with an identity transition: either this posts first and transition
        // proceeds to cancel it, or the stale notification is rejected.
        sessionBoundary.runIfCurrent(sessionEpoch) {
            NotificationManagerCompat.from(applicationContext)
                .notify(NUDGE_NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    companion object {
        const val NUDGE_NOTIFICATION_ID = 1001
        private const val MAX_RETRIES = 3
    }
}
