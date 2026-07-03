package com.qolve.fluyo.data

/**
 * Anything that holds per-user state in memory or on disk and would leak across users
 * if not reset on sign-out. Implementations register themselves into a Hilt multibinding
 * set; [com.qolve.fluyo.data.repository.SupabaseAuthRepository.signOut] iterates the set
 * before tearing down the Supabase session.<
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
 *   • Reset any persisted user-scoped preferences (e.g. onboarding-completed flag).
 *   • NOT call into the network — sign-out is offline-resilient. Just zero local state.
 */
interface SessionScopedCache {
    suspend fun clearForSignOut()
}
