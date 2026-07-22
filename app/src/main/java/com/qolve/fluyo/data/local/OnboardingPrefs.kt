package com.qolve.fluyo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.StaleSessionException
import com.qolve.fluyo.domain.repository.SessionBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
    private val sessionBoundary: SessionBoundary,
) : SessionScopedCache {
    private val legacyKeyCompleted = booleanPreferencesKey("onboarding_completed")
    private val activeUserId = MutableStateFlow<String?>(null)

    /** Completion belongs to an Auth identity, never to the device as a whole. */
    val completed: Flow<Boolean> = combine(store.data, activeUserId) { prefs, authId ->
        authId?.let { prefs[completedKey(it)] } ?: false
    }

    /**
     * Selects the identity whose onboarding state may be observed or changed. The one-time
     * legacy migration preserves onboarding for the first existing account after upgrade,
     * while preventing the old global flag from leaking to later accounts.
     */
    suspend fun activateUser(authId: String?) {
        if (authId != null) {
            val scopedKey = completedKey(authId)
            store.edit { prefs ->
                val legacyValue = prefs[legacyKeyCompleted]
                if (prefs[scopedKey] == null && legacyValue != null) {
                    prefs[scopedKey] = legacyValue
                }
                prefs.remove(legacyKeyCompleted)
            }
        }
        activeUserId.value = authId
    }

    suspend fun setCompleted(value: Boolean, expectedSessionEpoch: Long) {
        val authId = activeUserId.value ?: error("No active user for onboarding state")
        store.edit { prefs ->
            val accepted = sessionBoundary.runIfCurrent(expectedSessionEpoch) {
                prefs[completedKey(authId)] = value
            }
            if (!accepted) throw StaleSessionException()
        }
    }

    /**
     * Deactivate the current scope without deleting its completion. A returning user keeps
     * their onboarding result, while another identity sees only its own scoped value.
     */
    override suspend fun clearForSignOut() {
        activeUserId.value = null
    }

    /** Permanently removes local state after the corresponding account is deleted. */
    suspend fun forgetUser(authId: String) {
        store.edit { prefs -> prefs.remove(completedKey(authId)) }
        if (activeUserId.value == authId) activeUserId.value = null
    }

    private fun completedKey(authId: String) =
        booleanPreferencesKey("onboarding_completed_$authId")
}
