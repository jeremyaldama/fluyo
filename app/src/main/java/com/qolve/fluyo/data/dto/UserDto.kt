package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    @SerialName("auth_id") val authId: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("monthly_budget") val monthlyBudget: Double = 0.0,
    val currency: String = "PEN",
    val level: Int = 1,
    @SerialName("total_points") val totalPoints: Int = 0,
)

@Serializable
data class UserUpsertDto(
    @SerialName("auth_id") val authId: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class UserProfileUpdateDto(
    @SerialName("monthly_budget") val monthlyBudget: Double? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
)
