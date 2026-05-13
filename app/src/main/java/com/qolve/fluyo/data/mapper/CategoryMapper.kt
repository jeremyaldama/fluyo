package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.CategoryDto
import com.qolve.fluyo.domain.model.Category

fun CategoryDto.toDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isDefault = isDefault,
    displayOrder = displayOrder,
)
