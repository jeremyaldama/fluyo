package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.WhatsAppLinkChallengeDto
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.data.dto.WhatsAppLinkDto
import com.qolve.fluyo.data.dto.WhatsAppUnlinkResultDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.WhatsAppLink
import com.qolve.fluyo.domain.model.WhatsAppLinkChallenge
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.WhatsAppLinkRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.suspendRunCatching
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseWhatsAppLinkRepository @Inject constructor(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val sessionBoundary: SessionBoundary,
) : WhatsAppLinkRepository {

    override suspend fun currentLink(): Result<WhatsAppLink?> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = authRepository.currentUserId() ?: error("No authenticated user")
        val link = client.postgrest.from("whatsapp_links")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeSingleOrNull<WhatsAppLinkDto>()
            ?.toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        link
    }

    override suspend fun createChallenge(): Result<WhatsAppLinkChallenge> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        authRepository.currentUserId() ?: error("No authenticated user")
        val challenge = client.postgrest.rpc("create_whatsapp_link_challenge")
            .decodeSingle<WhatsAppLinkChallengeDto>()
            .toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        challenge
    }

    override suspend fun unlink(): Result<Boolean> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        authRepository.currentUserId() ?: error("No authenticated user")
        val unlinked = client.postgrest.rpc("unlink_whatsapp_link")
            .decodeSingle<WhatsAppUnlinkResultDto>()
            .unlinked
        sessionBoundary.requireCurrent(sessionEpoch)
        unlinked
    }
}
