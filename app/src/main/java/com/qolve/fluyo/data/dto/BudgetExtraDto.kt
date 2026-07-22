package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BudgetExtraDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    /** Always the first day of the month, ISO "yyyy-MM-dd". */
    val month: String,
    val amount: Double,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BudgetExtraInsertDto(
    @SerialName("user_id") val userId: String,
    val month: String,
    val amount: Double,
    val note: String? = null,
)

@Serializable
data class BudgetExtraCreateRpcParams(
    @SerialName("p_request_id") val requestId: String,
    @SerialName("p_amount") val amount: Double,
    @SerialName("p_note") val note: String? = null,
    @SerialName("p_month") val month: String,
)
