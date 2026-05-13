package com.qolve.fluyo.domain.model

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean,
    val displayOrder: Int,
)
