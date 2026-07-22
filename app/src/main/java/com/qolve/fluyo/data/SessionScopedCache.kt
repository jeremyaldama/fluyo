package com.qolve.fluyo.data

/**
 * Anything that holds per-user state in memory or on disk and would leak across users
 * if not reset on sign-out. Implementations register themselves into a Hilt multibinding
 * set; the session identity coordinator iterates the set before exposing a new identity.
 *
 * **Why this exists.** Every repository in this app is `@Singleton` and holds a
 * `MutableStateFlow` cache of the current user's data. When user A signs out and user B
 * signs in on the same device, those flows still hold user A's data until each ViewModel's
 * `refresh()` fires — and the UI renders the stale cache for a frame before that happens.
 * Worse, [com.qolve.fluyo.data.local.OnboardingPrefs.completed] is a per-device boolean,
 * so user B is routed straight to the home shell, skipping onboarding entirely. This
 * contract makes the reset explicit and uniform across all cache holders.
 *
 * Implementations must:
 *   • Reset every in-memory `MutableStateFlow`/`MutableSharedFlow` to its empty/default value.
 *   • Reset any persisted state that is not explicitly keyed by user identity.
 *   • NOT call into the network. Just zero or deactivate local state.
 */
interface SessionScopedCache {
    /**
     * Kept under its original name for source compatibility, but invoked for every identity
     * transition after the process has established its initial identity (A→B or
     * A→signed-out), not only explicit sign-out. Fresh process state is already empty.
     */
    suspend fun clearForSignOut()
}

/**
 * Marker for a one-shot cache that must survive the first identity restoration in a new
 * process. Normal caches are still cleared because WorkManager may have populated them
 * before the Activity/root coordinator is created.
 */
interface ColdStartRetainedCache : SessionScopedCache
