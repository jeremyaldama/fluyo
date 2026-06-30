package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val icon: String,
    val color: String,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0,
)

@Serializable
data class CategoryInsertDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val color: String,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0,
)

@Serializable
data class CategoryUpdateDto(
    val name: String? = null,
    val icon: String? = null,
    val color: String? = null,
)
