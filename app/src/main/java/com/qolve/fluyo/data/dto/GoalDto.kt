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
    @SerialName("deposit_count") val depositCount: Int = 0,
)

/** Parameters for the idempotent `create_goal` database RPC. */
@Serializable
data class GoalCreateRpcParams(
    @SerialName("p_request_id") val requestId: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_target_amount") val targetAmount: Double,
    @SerialName("p_deadline") val deadline: String?,
)

@Serializable
data class GoalArchiveRpcParams(
    @SerialName("p_goal_id") val goalId: String,
)

@Serializable
data class GoalArchiveRpcResultDto(
    val archived: Boolean,
)

/** Parameters for the atomic `deposit_to_goal` database RPC. */
@Serializable
data class GoalDepositRpcParams(
    @SerialName("p_goal_id") val goalId: String,
    @SerialName("p_amount") val amount: Double,
    @SerialName("p_request_id") val requestId: String,
)

/** Goal snapshot returned by `deposit_to_goal`, including the transition result. */
@Serializable
data class GoalDepositRpcResultDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double,
    @SerialName("current_amount") val currentAmount: Double,
    val deadline: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("deposit_count") val depositCount: Int,
    @SerialName("just_completed") val justCompleted: Boolean,
)
