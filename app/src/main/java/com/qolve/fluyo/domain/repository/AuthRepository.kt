package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun signOut(): Result<Unit>

    /** Upsert a row in `public.users` keyed by the current auth user. Triggers default-category seed. */
    suspend fun ensureUserRow(): Result<User>

    suspend fun currentUser(): Result<User?>

    /** Returns the `public.users.id` UUID for the signed-in user (cached). */
    suspend fun currentUserId(): String?

    suspend fun updateProfile(monthlyBudget: Double?, phoneNumber: String?): Result<User>
}
