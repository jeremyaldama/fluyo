package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.CategoryDto
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
) : CategoryRepository {

    private val state = MutableStateFlow<List<Category>>(emptyList())

    override fun observeCategories(): Flow<List<Category>> = state.asStateFlow()

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
}
