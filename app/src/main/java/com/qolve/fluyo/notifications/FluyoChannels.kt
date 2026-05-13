package com.qolve.fluyo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object FluyoChannels {
    const val NUDGES_ID = "fluyo_nudges"
    const val NUDGES_NAME = "Recordatorios y nudges"
    const val NUDGES_DESCRIPTION = "Resúmenes diarios, recordatorios y avances de tus metas."

    /** Idempotent — safe to call on every app start. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            NUDGES_ID,
            NUDGES_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = NUDGES_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }
}
