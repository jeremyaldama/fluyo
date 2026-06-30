package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.dto.CategoryDto
import com.qolve.fluyo.data.dto.CategoryInsertDto
import com.qolve.fluyo.data.dto.CategoryUpdateDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseCategoryRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) : CategoryRepository, SessionScopedCache {

    private val state = MutableStateFlow<List<Category>>(emptyList())

    override fun observeCategories(): Flow<List<Category>> = state.asStateFlow()

    override suspend fun clearForSignOut() {
        state.value = emptyList()
    }

    override suspend fun refresh(): Result<Unit> = runCatching {
        val userId = authRepository.currentUserId() ?: return@runCatching
        state.value = client.postgrest.from("categories")
            .select {
                filter { eq("user_id", userId) }
                order("display_order", Order.ASCENDING)
            }
            .decodeList<CategoryDto>()
            .map { it.toDomain() }
    }

    override suspend fun createCategory(
        name: String,
        icon: String,
        color: String,
    ): Result<Category> = runCatching {
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        val nextOrder = (state.value.maxOfOrNull { it.displayOrder } ?: 0) + 1
        val created = client.postgrest.from("categories")
            .insert(
                CategoryInsertDto(
                    userId = userId,
                    name = name.trim(),
                    icon = icon,
                    color = color,
                    isDefault = false,
                    displayOrder = nextOrder,
                ),
            ) { select() }
            .decodeSingle<CategoryDto>()
            .toDomain()
        refresh()
        created
    }

    override suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
    ): Result<Category> = runCatching {
        val updated = client.postgrest.from("categories")
            .update(
                CategoryUpdateDto(name = name.trim(), icon = icon, color = color),
            ) {
                filter { eq("id", id) }
                select()
            }
            .decodeSingle<CategoryDto>()
            .toDomain()
        refresh()
        updated
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        client.postgrest.from("categories")
            .delete { filter { eq("id", id) } }
        refresh()
    }
}
