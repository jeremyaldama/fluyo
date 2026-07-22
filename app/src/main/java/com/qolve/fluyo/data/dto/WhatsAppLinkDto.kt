package com.qolve.fluyo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppLinkDto(
    @SerialName("user_id") val userId: String,
    @SerialName("phone_e164") val phoneE164: String,
    @SerialName("verified_at") val verifiedAt: String,
)

@Serializable
data class WhatsAppLinkChallengeDto(
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
) {
    override fun toString(): String =
        "WhatsAppLinkChallengeDto(token=<redacted>, expiresAt=$expiresAt)"
}

@Serializable
data class WhatsAppUnlinkResultDto(
    val unlinked: Boolean,
)
