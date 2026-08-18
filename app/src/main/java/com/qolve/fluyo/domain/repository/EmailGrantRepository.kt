package com.qolve.fluyo.domain.repository

import com.qolve.fluyo.domain.model.EmailGrant

/**
 * Gmail receipt-import connection operations.
 *
 * The app sees public connection metadata plus the short-lived authorization code and opaque
 * encrypted state needed to finish the handshake. Google tokens remain server-side, and Edge
 * validates the state cryptographically before exchanging the code.
 */
interface EmailGrantRepository {
    /** Returns the user's current Gmail grant, or null when no account is linked. */
    suspend fun linkedGrant(): Result<EmailGrant?>

    /** Starts an authenticated OAuth handshake and returns the Google consent URL. */
    suspend fun createAuthorizationUrl(redirectUri: String): Result<String>

    /**
     * Completes OAuth after the app receives Google's short-lived code and the opaque state.
     * Both values are sent in an authenticated request body and are never placed in logs.
     */
    suspend fun completeAuthorization(authorizationCode: String, state: String): Result<Unit>

    /** Deletes the user's Fluyo Gmail grant and Vault credential. Idempotent. */
    suspend fun disconnect(): Result<Int>
}

/** Stable, non-sensitive failures that gmail-connect is allowed to expose to Android. */
enum class GmailAuthorizationFailure {
    INVALID_REQUEST,
    INVALID_STATE,
    STATE_EXPIRED,
    STATE_USER_MISMATCH,
    UNAUTHORIZED,
    USER_NOT_FOUND,
    OAUTH_EXCHANGE,
    MISSING_SCOPE,
    PROFILE_LOOKUP,
    WATCH_SETUP,
    ACCOUNT_CONFLICT,
    GRANT_STORAGE,
    SERVER_NOT_CONFIGURED,
    SERVER,
    UNKNOWN;

    companion object {
        fun fromServerCode(code: String?): GmailAuthorizationFailure = when (code) {
            "invalid_request" -> INVALID_REQUEST
            "invalid_state" -> INVALID_STATE
            "state_expired" -> STATE_EXPIRED
            "state_user_mismatch" -> STATE_USER_MISMATCH
            "unauthorized" -> UNAUTHORIZED
            "user_not_found" -> USER_NOT_FOUND
            "oauth_exchange_failed" -> OAUTH_EXCHANGE
            "missing_scope" -> MISSING_SCOPE
            "profile_failed" -> PROFILE_LOOKUP
            "watch_failed" -> WATCH_SETUP
            "account_conflict" -> ACCOUNT_CONFLICT
            "grant_store_failed" -> GRANT_STORAGE
            "server_not_configured" -> SERVER_NOT_CONFIGURED
            "server_error" -> SERVER
            else -> UNKNOWN
        }
    }
}

/** Carries only a whitelisted failure enum; the raw HTTP/provider error is discarded. */
class GmailAuthorizationException(
    val reason: GmailAuthorizationFailure,
) : Exception(reason.name)
