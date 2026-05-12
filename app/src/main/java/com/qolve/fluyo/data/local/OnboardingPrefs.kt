package com.qolve.fluyo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    private val keyCompleted = booleanPreferencesKey("onboarding_completed")

    val completed: Flow<Boolean> = store.data.map { it[keyCompleted] ?: false }

    suspend fun setCompleted(value: Boolean) {
        store.edit { it[keyCompleted] = value }
    }
}
