package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun refresh(): Result<Unit>

    /** Create a custom category (HU-11). Appended after the existing display order. */
    suspend fun createCategory(name: String, icon: String, color: String): Result<Category>

    /** Rename / restyle an existing category. */
    suspend fun updateCategory(id: String, name: String, icon: String, color: String): Result<Category>

    /** Delete a category. Past expenses keep working — their `category_id` is set null. */
    suspend fun deleteCategory(id: String): Result<Unit>
}
