package com.qolve.fluyo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.qolve.fluyo.notifications.FluyoChannels
import com.qolve.fluyo.data.local.SensitiveCacheCleaner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FluyoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sensitiveCacheCleaner: SensitiveCacheCleaner

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        sensitiveCacheCleaner.deleteStaleFiles()
        FluyoChannels.ensureCreated(this)
    }
}
