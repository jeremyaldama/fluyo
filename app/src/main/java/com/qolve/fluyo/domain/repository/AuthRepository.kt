package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    /** Sign in with email + password. Caller should follow up with [ensureUserRow] on success. */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>

    /**
     * Create a new email/password account. The `displayName` is stored on Supabase Auth's
     * user metadata so it surfaces consistently across reads. Caller should follow up with
     * [ensureUserRow] on success.
     */
    suspend fun signUpWithEmail(email: String, password: String, displayName: String?): Result<Unit>

    suspend fun signOut(): Result<Unit>

    /**
     * Deletes the user's `public.users` row — `ON DELETE CASCADE` removes their expenses,
     * goals, deposits, badges and categories — then signs out (HU-11). The Supabase Auth
     * `auth.users` record itself remains (removing it needs service-role privileges, out of
     * scope for the client).
     */
    suspend fun deleteAccount(): Result<Unit>

    /** Upsert a row in `public.users` keyed by the current auth user. Triggers default-category seed. */
    suspend fun ensureUserRow(): Result<User>

    suspend fun currentUser(): Result<User?>

    /** Returns the `public.users.id` UUID for the signed-in user (cached). */
    suspend fun currentUserId(): String?

    suspend fun updateProfile(
        monthlyBudget: Double? = null,
        phoneNumber: String? = null,
        currency: String? = null,
    ): Result<User>

    /** Persists the badge-derived XP rollup (`total_points`, `level`) on `public.users`. */
    suspend fun updateGamification(totalPoints: Int, level: Int): Result<Unit>

    /** Updates the notification-related columns on `public.users`. Returns the refreshed row. */
    suspend fun updateNotificationSettings(
        enabled: Boolean? = null,
        hour: Int? = null,
        types: Set<com.qolve.fluyo.domain.model.NudgeType>? = null,
    ): Result<User>
}
