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
import com.qolve.fluyo.MainActivity
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.repository.BadgeNotificationGateway
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.presentation.util.descriptionRes
import com.qolve.fluyo.presentation.util.nameRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a system notification when a badge unlocks (HU-08). The in-app snackbar
 * (via [com.qolve.fluyo.presentation.events.AppEvents]) covers the foreground case;
 * this covers unlocks that happen off-screen (e.g. the daily worker awarding
 * `saver_month`). Mirrors the notification-building pattern in [NudgeWorker].
 */
@Singleton
class BadgeNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionBoundary: SessionBoundary,
) : BadgeNotificationGateway {
    override fun notifyBadgeUnlocked(type: BadgeType, expectedSessionEpoch: Long) {
        sessionBoundary.runIfCurrent(expectedSessionEpoch) {
            postNotification(type)
        }
    }

    private fun postNotification(type: BadgeType) {
        FluyoChannels.ensureCreated(context)

        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!canPost) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            type.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = context.getString(R.string.badge_unlocked_prefix)
        val body = context.getString(type.nameRes()) + " · " + context.getString(type.descriptionRes())

        val notification = NotificationCompat.Builder(context, FluyoChannels.LOGROS_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context)
            .notify(BADGE_NOTIFICATION_BASE_ID + type.ordinal, notification)
    }

    companion object {
        const val BADGE_NOTIFICATION_BASE_ID = 2000
    }
}
