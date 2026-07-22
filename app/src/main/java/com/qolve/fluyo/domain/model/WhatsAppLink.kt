package com.qolve.fluyo.domain.model

import java.time.Instant

private val E164_PATTERN = Regex("^\\+[1-9][0-9]{7,14}$")
private val CHALLENGE_TOKEN_PATTERN = Regex("^[0-9a-f]{32}$")

/** A WhatsApp identity whose sender was verified by the trusted webhook backend. */
data class WhatsAppLink(
    val phoneE164: String,
    val verifiedAt: Instant,
) {
    init {
        require(E164_PATTERN.matches(phoneE164)) { "Invalid verified WhatsApp number" }
    }

    /** Keeps the settings screen useful without displaying the full number at a glance. */
    val maskedPhone: String
        get() = "•••• ${phoneE164.takeLast(4)}"

    override fun toString(): String =
        "WhatsAppLink(phoneE164=<redacted>, verifiedAt=$verifiedAt)"
}

/** Short-lived, single-use proof that must be sent from the user's WhatsApp account. */
data class WhatsAppLinkChallenge(
    val token: String,
    val expiresAt: Instant,
) {
    init {
        require(CHALLENGE_TOKEN_PATTERN.matches(token)) { "Invalid WhatsApp link challenge" }
    }

    /** The backend contract is exact so the webhook can parse it without ambiguity. */
    val message: String
        get() = "VINCULAR FLUYO $token"

    /** Never reveal the single-use token through incidental debug output. */
    override fun toString(): String =
        "WhatsAppLinkChallenge(token=<redacted>, expiresAt=$expiresAt)"
}
