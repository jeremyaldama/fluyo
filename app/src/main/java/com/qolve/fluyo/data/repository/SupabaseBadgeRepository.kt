package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.publishIfCurrent
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.data.dto.BadgeDto
import com.qolve.fluyo.data.dto.BadgeUnlockRpcParams
import com.qolve.fluyo.data.dto.BadgeUnlockRpcResultDto
import com.qolve.fluyo.data.mapper.toDomainOrNull
import com.qolve.fluyo.domain.model.Badge
import com.qolve.fluyo.domain.model.BadgeType
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.suspendRunCatching
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class SupabaseBadgeRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val sessionBoundary: SessionBoundary,
) : BadgeRepository, SessionScopedCache {

    private val state = MutableStateFlow<List<Badge>>(emptyList())

    override fun observeBadges(): Flow<List<Badge>> = state.asStateFlow()

    override suspend fun clearForSignOut() {
        state.value = emptyList()
    }

    override suspend fun refresh(): Result<Unit> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: return@suspendRunCatching
        val badges = fetchBadges(userId, sessionEpoch)
        sessionBoundary.publishIfCurrent(sessionEpoch) { state.value = badges }
    }

    private suspend fun fetchBadges(userId: String, sessionEpoch: Long): List<Badge> =
        collectPostgrestPages { range ->
            val page = client.postgrest.from("badges")
                .select {
                    filter { eq("user_id", userId) }
                    order("unlocked_at", Order.ASCENDING)
                    order("id", Order.ASCENDING)
                    range(range)
                }
                .decodeList<BadgeDto>()
            sessionBoundary.requireCurrent(sessionEpoch)
            page
        }.mapNotNull { it.toDomainOrNull() }

    override suspend fun unlockIfMissing(type: BadgeType): Result<Boolean> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        // The server serializes this badge key, revalidates its database-backed
        // criterion and reports whether this call inserted it.
        if (state.value.any { it.type == type }) return@suspendRunCatching false

        val outcome = client.postgrest.rpc(
            function = "unlock_badge",
            parameters = Json.encodeToJsonElement(
                BadgeUnlockRpcParams(badgeType = type.wire),
            ).jsonObject,
        ).decodeSingle<BadgeUnlockRpcResultDto>()
        val badges = fetchBadges(userId, sessionEpoch)
        sessionBoundary.publishIfCurrent(sessionEpoch) { state.value = badges }
        outcome.unlocked
    }
}
