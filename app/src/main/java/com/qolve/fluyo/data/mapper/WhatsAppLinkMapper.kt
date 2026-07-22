package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.WhatsAppLinkChallengeDto
import com.qolve.fluyo.data.dto.WhatsAppLinkDto
import com.qolve.fluyo.domain.model.WhatsAppLink
import com.qolve.fluyo.domain.model.WhatsAppLinkChallenge
import java.time.Instant
import java.time.OffsetDateTime

fun WhatsAppLinkDto.toDomain(): WhatsAppLink = WhatsAppLink(
    phoneE164 = phoneE164,
    verifiedAt = verifiedAt.toWhatsAppInstant(),
)

fun WhatsAppLinkChallengeDto.toDomain(): WhatsAppLinkChallenge = WhatsAppLinkChallenge(
    token = token,
    expiresAt = expiresAt.toWhatsAppInstant(),
)

private fun String.toWhatsAppInstant(): Instant =
    runCatching { Instant.parse(this) }
        .recoverCatching { OffsetDateTime.parse(this).toInstant() }
        .getOrElse { error("Invalid WhatsApp timestamp") }
