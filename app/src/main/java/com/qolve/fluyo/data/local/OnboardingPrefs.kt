package com.qolve.fluyo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.qolve.fluyo.data.SessionScopedCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) : SessionScopedCache {
    private val keyCompleted = booleanPreferencesKey("onboarding_completed")

    val completed: Flow<Boolean> = store.data.map { it[keyCompleted] ?: false }

    suspend fun setCompleted(value: Boolean) {
        store.edit { it[keyCompleted] = value }
    }

    /**
     * Reset onboarding completion to false. Critical: this flag is per-DEVICE in DataStore,
     * not per-user. Without this reset, user A onboards → flag = true → user A signs out →
     * user B signs in → flag still true → user B skips onboarding and lands on Home with a
     * blank slate. Worse, [com.qolve.fluyo.presentation.navigation.RootViewModel] uses this
     * flag to decide between ONBOARDING and MAIN, so leaving it stale also leaks the routing
     * decision across accounts.
     */
    override suspend fun clearForSignOut() {
        setCompleted(false)
    }
}
