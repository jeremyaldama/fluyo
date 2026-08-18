package com.qolve.fluyo.domain.model

import java.time.Instant

/**
 * Public, non-secret Gmail connection metadata exposed to the Android app.
 *
 * OAuth tokens never cross this boundary. [lastError] is a bounded machine code
 * written by the ingestion backend and mapped through an allow-list before display.
 */
data class EmailGrant(
    val email: String,
    val watchExpiration: Instant?,
    val lastError: String?,
)
