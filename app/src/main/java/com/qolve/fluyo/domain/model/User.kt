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
)
