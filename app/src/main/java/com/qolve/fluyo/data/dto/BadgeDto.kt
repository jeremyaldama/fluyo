package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BadgeDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("badge_type") val badgeType: String,
    val name: String,
    val description: String? = null,
    val criteria: String? = null,
    @SerialName("unlocked_at") val unlockedAt: String,
)

@Serializable
data class BadgeUnlockRpcParams(
    @SerialName("p_badge_type") val badgeType: String,
)

@Serializable
data class BadgeUnlockRpcResultDto(
    val unlocked: Boolean,
)
