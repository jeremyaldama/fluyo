package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun refresh(): Result<Unit>
}
