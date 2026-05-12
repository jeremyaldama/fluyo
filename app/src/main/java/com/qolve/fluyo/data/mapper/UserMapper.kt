package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.domain.model.User

fun UserDto.toDomain(): User = User(
    id = id,
    authId = authId,
    email = email,
    displayName = displayName,
    phoneNumber = phoneNumber,
    monthlyBudget = monthlyBudget,
    currency = currency,
    level = level,
    totalPoints = totalPoints,
)
