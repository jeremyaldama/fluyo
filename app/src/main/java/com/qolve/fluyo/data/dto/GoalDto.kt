package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    val deadline: String? = null,
    val status: String = "active",
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class GoalInsertDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double,
    val deadline: String? = null,
)

@Serializable
data class GoalUpdateDto(
    @SerialName("current_amount") val currentAmount: Double? = null,
    val status: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class GoalDepositDto(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class GoalDepositInsertDto(
    @SerialName("goal_id") val goalId: String,
    @SerialName("user_id") val userId: String,
    val amount: Double,
)
