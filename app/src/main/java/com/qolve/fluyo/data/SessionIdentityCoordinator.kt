package com.qolve.fluyo.data

import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.data.local.NudgePrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fail-closed boundary between authenticated identities.
 *
 * Before a new identity can be provisioned or routed to user content, every singleton
 * cache is reset and user-scoped preferences are switched atomically. Repeated auth events
 * for the same identity (for example foreground refreshes) are intentionally idempotent.
 */
@Singleton
class SessionIdentityCoordinator @Inject constructor(
    private val sessionCaches: dagger.Lazy<Set<@JvmSuppressWildcards SessionScopedCache>>,
    private val onboardingPrefs: OnboardingPrefs,
    private val nudgePrefs: NudgePrefs,
    private val sessionEpoch: SessionEpoch,
) {
    private val mutex = Mutex()
    private var initialized = false
    private var activeAuthId: String? = null

    suspend fun transitionTo(authId: String?): Boolean = mutex.withLock {
        if (initialized && activeAuthId == authId) return@withLock false

        // Invalidate in-flight work before clearing caches. SessionEpoch serializes this
        // increment with guarded cache/event publications, closing the post-clear race.
        sessionEpoch.beginTransition()

        var firstFailure: Throwable? = null

        // WorkManager can populate repositories before the Activity/root coordinator exists,
        // so normal caches are cleared even on first restoration. Only explicitly marked
        // one-shot cold-start input (the incoming share URI) survives that first boundary.
        sessionCaches.get().forEach { cache ->
            if (initialized || cache !is ColdStartRetainedCache) {
                try {
                    cache.clearForSignOut()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    if (firstFailure == null) firstFailure = failure
                }
            }
        }

        if (firstFailure == null) {
            try {
                onboardingPrefs.activateUser(authId)
                nudgePrefs.activateUser(authId)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                firstFailure = failure
            }
        }

        initialized = true
        activeAuthId = if (firstFailure == null) authId else null
        // Signed-out is a closed boundary. Supabase may still expose the old session for
        // a short window while signOut()/RefreshFailure cleanup runs, so no new work is
        // accepted until a concrete SignedIn identity completes provisioning.
        if (firstFailure == null && authId != null) sessionEpoch.completeTransition()
        firstFailure?.let { throw it }
        true
    }

    suspend fun currentIdentity(): String? = mutex.withLock { activeAuthId }

    /** Purges preferences that normal sign-out intentionally retains for a returning user. */
    suspend fun forgetUser(authId: String) = mutex.withLock {
        var firstFailure: Exception? = null
        suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (failure: Exception) {
                if (firstFailure == null) {
                    firstFailure = failure
                } else {
                    firstFailure?.addSuppressed(failure)
                }
            }
        }

        // Account deletion is irreversible remotely. Attempt every local purge even if one
        // DataStore fails, then preserve the first failure (including cancellation) for callers.
        attempt { onboardingPrefs.forgetUser(authId) }
        attempt { nudgePrefs.forgetUser(authId) }
        firstFailure?.let { throw it }
    }
}
