package com.qolve.fluyo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.domain.repository.NudgeHistoryRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.data.StaleSessionException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import com.qolve.fluyo.domain.time.FluyoTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the most recently fired nudge so we never fire more than one per day
 * (per CLAUDE.md "max 1/day"). Persists across worker invocations.
 */
@Singleton
class NudgePrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val sessionBoundary: SessionBoundary,
) : SessionScopedCache, NudgeHistoryRepository {
    private val legacyLastFiredDateKey = stringPreferencesKey("last_nudge_date")
    private val persistedActiveUserIdKey = stringPreferencesKey("active_nudge_auth_id")
    private val activeUserId = MutableStateFlow<String?>(null)

    /** Selects the identity whose daily rate limit is active without deleting its history. */
    suspend fun activateUser(authId: String?) {
        if (authId != null) {
            val scopedKey = lastFiredDateKey(authId)
            dataStore.edit { prefs ->
                val legacyValue = prefs[legacyLastFiredDateKey]
                if (prefs[scopedKey] == null && legacyValue != null) {
                    prefs[scopedKey] = legacyValue
                }
                prefs.remove(legacyLastFiredDateKey)
                prefs[persistedActiveUserIdKey] = authId
            }
        } else {
            dataStore.edit { prefs -> prefs.remove(persistedActiveUserIdKey) }
        }
        activeUserId.value = authId
    }

    override suspend fun lastFiredOn(expectedSessionEpoch: Long): LocalDate? {
        if (!sessionBoundary.isCurrent(expectedSessionEpoch)) throw StaleSessionException()
        val authId = resolvedActiveUserId() ?: return null
        val stored = dataStore.data.map { it[lastFiredDateKey(authId)] }.first()
        if (!sessionBoundary.isCurrent(expectedSessionEpoch)) throw StaleSessionException()
        return stored?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    override suspend fun claimToday(expectedSessionEpoch: Long): Boolean {
        val authId = resolvedActiveUserId()
            ?: error("No active user for nudge rate limiting")
        val today = FluyoTime.today().toString()
        var claimed = false
        dataStore.edit { prefs ->
            val accepted = sessionBoundary.runIfCurrent(expectedSessionEpoch) {
                val key = lastFiredDateKey(authId)
                if (prefs[key] != today) {
                    prefs[key] = today
                    claimed = true
                }
            }
            if (!accepted) throw StaleSessionException()
        }
        return claimed
    }

    override suspend fun clearForSignOut() {
        activeUserId.value = null
        dataStore.edit { prefs -> prefs.remove(persistedActiveUserIdKey) }
    }

    /** Permanently removes the deleted account's rate-limit metadata. */
    suspend fun forgetUser(authId: String) {
        dataStore.edit { prefs ->
            prefs.remove(lastFiredDateKey(authId))
            if (prefs[persistedActiveUserIdKey] == authId) {
                prefs.remove(persistedActiveUserIdKey)
            }
        }
        if (activeUserId.value == authId) activeUserId.value = null
    }

    private suspend fun resolvedActiveUserId(): String? =
        activeUserId.value
            ?: dataStore.data.map { it[persistedActiveUserIdKey] }.first()

    private fun lastFiredDateKey(authId: String) =
        stringPreferencesKey("last_nudge_date_$authId")
}
