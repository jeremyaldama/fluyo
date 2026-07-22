package com.qolve.fluyo.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.qolve.fluyo.domain.time.FluyoTime
import com.qolve.fluyo.data.SessionEpoch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NudgePrefsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `headless process recovers persisted identity rate limit`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.newFile("nudges.preferences_pb") },
        )
        val boundary = SessionEpoch()
        val foregroundInstance = NudgePrefs(store, boundary)
        foregroundInstance.activateUser("auth-a")
        foregroundInstance.claimToday(boundary.snapshot())

        // A WorkManager-started process has a fresh NudgePrefs instance and no
        // RootViewModel, but it shares the persisted DataStore.
        val headlessInstance = NudgePrefs(store, boundary)

        assertEquals(FluyoTime.today(), headlessInstance.lastFiredOn(boundary.snapshot()))
    }
}
