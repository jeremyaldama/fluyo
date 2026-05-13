package com.qolve.fluyo.notifications

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires the nudge worker once, immediately. For manual smoke tests
 * triggered from the Profile screen — saves you from waiting until 20:00.
 */
@Singleton
class NudgeOneShot @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun fireNow() {
        val request = OneTimeWorkRequestBuilder<NudgeWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
