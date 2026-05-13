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
import com.qolve.fluyo.data.local.NudgePrefs
import com.qolve.fluyo.domain.usecase.ComputeNudgeUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NudgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val computeNudge: ComputeNudgeUseCase,
    private val nudgePrefs: NudgePrefs,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val nudge = runCatching { computeNudge() }.getOrNull() ?: return Result.success()

        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!canPost) return Result.success()

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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(nudge.title)
            .setContentText(nudge.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NUDGE_NOTIFICATION_ID, notification)

        nudgePrefs.markFiredToday()
        return Result.success()
    }

    companion object {
        const val NUDGE_NOTIFICATION_ID = 1001
    }
}
