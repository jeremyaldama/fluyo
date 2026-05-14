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
    @SerialName("notification_enabled") val notificationEnabled: Boolean = true,
    @SerialName("notification_hour") val notificationHour: Int = 20,
    @SerialName("notification_types")
    val notificationTypes: List<String> = listOf("progress", "reminder", "budget", "goal"),
    /** ISO-8601 timestamp from `users.created_at` — surfaces as "Desde [mes año]" on Profile. */
    @SerialName("created_at") val createdAt: String? = null,
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

@Serializable
data class NotificationSettingsUpdateDto(
    @SerialName("notification_enabled") val notificationEnabled: Boolean? = null,
    @SerialName("notification_hour") val notificationHour: Int? = null,
    @SerialName("notification_types") val notificationTypes: List<String>? = null,
)
