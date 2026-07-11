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

@Serializable
data class ExpenseInsertDto(
    @SerialName("user_id") val userId: String,
    val amount: Double,
    @SerialName("category_id") val categoryId: String? = null,
    val description: String? = null,
    @SerialName("expense_date") val expenseDate: String,
    val source: String,
    val recipient: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

/** Partial update for user-editable fields; source/recipient/image are preserved. */
@Serializable
data class ExpenseUpdateDto(
    val amount: Double,
    @SerialName("category_id") val categoryId: String? = null,
    val description: String? = null,
    @SerialName("expense_date") val expenseDate: String,
)

@Serializable
data class CurrentMonthBudgetDto(
    @SerialName("user_id") val userId: String,
    /** EFFECTIVE budget: base users.monthly_budget + this month's extras (view 0005). */
    @SerialName("monthly_budget") val monthlyBudget: Double = 0.0,
    @SerialName("total_spent") val totalSpent: Double = 0.0,
    val remaining: Double = 0.0,
    /** This month's "ingreso extra" total; default keeps compat with the pre-0005 view. */
    @SerialName("extra_income") val extraIncome: Double = 0.0,
)
