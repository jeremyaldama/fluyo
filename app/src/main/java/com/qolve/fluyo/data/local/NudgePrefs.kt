package com.qolve.fluyo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the most recently fired nudge so we never fire more than one per day
 * (per CLAUDE.md "max 1/day"). Persists across worker invocations.
 */
@Singleton
class NudgePrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val lastFiredDateKey = stringPreferencesKey("last_nudge_date")

    suspend fun lastFiredOn(): LocalDate? {
        val stored = dataStore.data.map { it[lastFiredDateKey] }.first()
        return stored?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    suspend fun markFiredToday() {
        val today = LocalDate.now().toString()
        dataStore.edit { it[lastFiredDateKey] = today }
    }
}
