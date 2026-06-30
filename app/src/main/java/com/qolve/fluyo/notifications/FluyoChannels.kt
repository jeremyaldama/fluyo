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

    const val LOGROS_ID = "fluyo_logros"
    const val LOGROS_NAME = "Logros y medallas"
    const val LOGROS_DESCRIPTION = "Avisos cuando desbloqueas una nueva insignia."

    /** Idempotent — safe to call on every app start. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(NUDGES_ID, NUDGES_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = NUDGES_DESCRIPTION },
        )
        manager.createNotificationChannel(
            NotificationChannel(LOGROS_ID, LOGROS_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = LOGROS_DESCRIPTION },
        )
    }
}
