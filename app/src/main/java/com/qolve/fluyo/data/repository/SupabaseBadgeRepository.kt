package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.BadgeDto
import com.qolve.fluyo.data.dto.BadgeInsertDto
import com.qolve.fluyo.data.mapper.toDomainOrNull
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private fun BadgeType.defaultName(): String = when (this) {
    BadgeType.FIRST_EXPENSE -> "Primer Registro"
    BadgeType.STREAK_7 -> "Racha Semanal"
    BadgeType.STREAK_30 -> "Racha Mensual"
    BadgeType.FIRST_GOAL -> "Primera Meta"
    BadgeType.SAVER_MONTH -> "Ahorrista del Mes"
}

@Singleton
class SupabaseBadgeRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
) : BadgeRepository {

    private val state = MutableStateFlow<List<Badge>>(emptyList())

    override fun observeBadges(): Flow<List<Badge>> = state.asStateFlow()

    override suspend fun refresh(): Result<Unit> = runCatching {
        val userId = authRepository.currentUserId() ?: return@runCatching
        state.value = client.postgrest.from("badges")
            .select {
                filter { eq("user_id", userId) }
                order("unlocked_at", Order.ASCENDING)
            }
            .decodeList<BadgeDto>()
            .mapNotNull { it.toDomainOrNull() }
    }

    override suspend fun unlockIfMissing(type: BadgeType): Result<Boolean> = runCatching {
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        // UNIQUE(user_id, badge_type) prevents duplicates — we still pre-check to
        // avoid emitting a duplicate event on conflict.
        if (state.value.any { it.type == type }) return@runCatching false

        client.postgrest.from("badges").insert(
            BadgeInsertDto(
                userId = userId,
                badgeType = type.wire,
                name = type.defaultName(),
            ),
        )
        refresh()
        true
    }
}
