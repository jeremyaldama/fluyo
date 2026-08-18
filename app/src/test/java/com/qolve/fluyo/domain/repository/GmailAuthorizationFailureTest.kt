package com.qolve.fluyo.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class GmailAuthorizationFailureTest {

    @Test
    fun `maps every documented completion error through a strict whitelist`() {
        val expected = mapOf(
            "invalid_request" to GmailAuthorizationFailure.INVALID_REQUEST,
            "invalid_state" to GmailAuthorizationFailure.INVALID_STATE,
            "state_expired" to GmailAuthorizationFailure.STATE_EXPIRED,
            "state_user_mismatch" to GmailAuthorizationFailure.STATE_USER_MISMATCH,
            "unauthorized" to GmailAuthorizationFailure.UNAUTHORIZED,
            "user_not_found" to GmailAuthorizationFailure.USER_NOT_FOUND,
            "oauth_exchange_failed" to GmailAuthorizationFailure.OAUTH_EXCHANGE,
            "missing_scope" to GmailAuthorizationFailure.MISSING_SCOPE,
            "profile_failed" to GmailAuthorizationFailure.PROFILE_LOOKUP,
            "watch_failed" to GmailAuthorizationFailure.WATCH_SETUP,
            "account_conflict" to GmailAuthorizationFailure.ACCOUNT_CONFLICT,
            "grant_store_failed" to GmailAuthorizationFailure.GRANT_STORAGE,
            "server_not_configured" to GmailAuthorizationFailure.SERVER_NOT_CONFIGURED,
            "server_error" to GmailAuthorizationFailure.SERVER,
        )

        expected.forEach { (code, failure) ->
            assertEquals(failure, GmailAuthorizationFailure.fromServerCode(code))
        }
    }

    @Test
    fun `does not retain or expose an unknown server error`() {
        assertEquals(
            GmailAuthorizationFailure.UNKNOWN,
            GmailAuthorizationFailure.fromServerCode("provider token=secret"),
        )
    }
}
