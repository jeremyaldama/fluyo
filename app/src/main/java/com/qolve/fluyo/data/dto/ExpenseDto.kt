package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    @SerialName("category_id") val categoryId: String? = null,
    val description: String? = null,
    @SerialName("expense_date") val expenseDate: String,
    val source: String,
    val recipient: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Parameters for the idempotent `create_expense` database RPC. */
@Serializable
data class ExpenseCreateRpcParams(
    @SerialName("p_request_id") val requestId: String,
    @SerialName("p_amount") val amount: Double,
    @SerialName("p_category_id") val categoryId: String?,
    @SerialName("p_description") val description: String?,
    @SerialName("p_expense_date") val expenseDate: String,
    @SerialName("p_source") val source: String,
    @SerialName("p_recipient") val recipient: String?,
    @SerialName("p_image_url") val imageUrl: String?,
)

@Serializable
data class ExpensePageRpcParams(
    @SerialName("p_from") val from: String,
    @SerialName("p_to") val to: String,
    @SerialName("p_snapshot_at") val snapshotAt: String?,
    @SerialName("p_before_created_at") val beforeCreatedAt: String?,
    @SerialName("p_before_id") val beforeId: String?,
    @SerialName("p_page_size") val pageSize: Int,
)

@Serializable
data class ExpensePageDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    @SerialName("category_id") val categoryId: String? = null,
    val description: String? = null,
    @SerialName("expense_date") val expenseDate: String,
    val source: String,
    val recipient: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("snapshot_at") val snapshotAt: String,
) {
    fun asExpenseDto(): ExpenseDto = ExpenseDto(
        id = id,
        userId = userId,
        amount = amount,
        categoryId = categoryId,
        description = description,
        expenseDate = expenseDate,
        source = source,
        recipient = recipient,
        imageUrl = imageUrl,
        createdAt = createdAt,
    )
}

@Serializable
data class ExpenseStreakRpcResultDto(
    val streak: Int,
)

@Serializable
data class CurrentMonthBudgetDto(
    @SerialName("user_id") val userId: String,
    /** EFFECTIVE budget: base users.monthly_budget + this month's extras (view 0005). */
    @SerialName("monthly_budget") val monthlyBudget: Double = 0.0,
    @SerialName("total_spent") val totalSpent: Double = 0.0,
    /** This month's "ingreso extra" total; default keeps compat with the pre-0005 view. */
    @SerialName("extra_income") val extraIncome: Double = 0.0,
)
