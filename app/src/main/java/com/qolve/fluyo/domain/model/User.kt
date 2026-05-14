package com.qolve.fluyo.domain.model

data class User(
    val id: String,
    val authId: String,
    val email: String?,
    val displayName: String?,
    val phoneNumber: String?,
    val monthlyBudget: Double,
    val currency: String,
    val level: Int,
    val totalPoints: Int,
    val notificationEnabled: Boolean = true,
    val notificationHour: Int = 20,
    val notificationTypes: Set<NudgeType> = NudgeType.entries.toSet(),
    /**
     * Profile photo URL — typically from Google sign-in. Not persisted to our `users` table:
     * read at runtime from Supabase Auth's user metadata. Null for email/password users without
     * an avatar set.
     */
    val avatarUrl: String? = null,
)
