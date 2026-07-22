package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.model.SignUpOutcome
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.model.MoneyAmount
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    /** Sign in with email + password. Root session coordination provisions the profile. */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>

    /**
     * Create a new email/password account. The `displayName` is stored on Supabase Auth's
     * user metadata so it surfaces consistently across reads. The result distinguishes an
     * immediate session from the normal "check your email" flow.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
    ): Result<SignUpOutcome>

    /** Requests a fresh confirmation email for an account whose sign-up is still pending. */
    suspend fun resendSignUpConfirmation(email: String): Result<Unit>

    suspend fun signOut(): Result<Unit>

    /** Deletes the Auth account and associated data through the authenticated server route. */
    suspend fun deleteAccount(): Result<Unit>

    /** Upsert a row in `public.users` keyed by the current auth user. Triggers default-category seed. */
    suspend fun ensureUserRow(): Result<User>

    suspend fun currentUser(): Result<User?>

    /** Returns the `public.users.id` UUID for the signed-in user (cached). */
    suspend fun currentUserId(): String?

    /** True once any expense, goal or monthly budget extra fixes the account denomination. */
    suspend fun hasMonetaryActivity(): Result<Boolean>

    suspend fun updateProfile(
        monthlyBudget: MoneyAmount? = null,
        currency: String? = null,
    ): Result<User>

    /** Updates the notification-related columns on `public.users`. Returns the refreshed row. */
    suspend fun updateNotificationSettings(
        enabled: Boolean? = null,
        hour: Int? = null,
        types: Set<com.qolve.fluyo.domain.model.NudgeType>? = null,
    ): Result<User>
}
