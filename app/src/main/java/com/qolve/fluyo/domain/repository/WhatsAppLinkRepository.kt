package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.WhatsAppLink
import com.qolve.fluyo.domain.model.WhatsAppLinkChallenge

interface WhatsAppLinkRepository {
    /** Reads only the authenticated user's backend-verified sender identity through RLS. */
    suspend fun currentLink(): Result<WhatsAppLink?>

    /** Creates a short-lived token. The app must never ask the user to type a phone number. */
    suspend fun createChallenge(): Result<WhatsAppLinkChallenge>

    /** Revokes the verified identity and invalidates every outstanding challenge. */
    suspend fun unlink(): Result<Boolean>
}
