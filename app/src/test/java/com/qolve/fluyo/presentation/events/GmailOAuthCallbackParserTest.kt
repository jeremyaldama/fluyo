package com.qolve.fluyo.presentation.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GmailOAuthCallbackParserTest {

    private val validState = "v1.abcdefghijklmnop.abcdefghijklmnopqrstuvw"

    @Test
    fun `accepts a bounded completion callback for the exact app target`() {
        val result = GmailOAuthCallbackParser.parse(
            scheme = GmailOAuthCallbackParser.SCHEME,
            host = GmailOAuthCallbackParser.HOST,
            status = "complete",
            code = "4_0-safe.Google/code+~=%",
            state = validState,
        )

        assertEquals(
            GmailOAuthCallback.Complete(
                authorizationCode = "4_0-safe.Google/code+~=%",
                state = validState,
            ),
            result,
        )
    }

    @Test
    fun `rejects missing malformed or oversized completion parameters`() {
        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_REQUEST),
            GmailOAuthCallbackParser.parse(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = GmailOAuthCallbackParser.HOST,
                status = "complete",
                code = "contains unsupported space",
                state = validState,
            ),
        )
        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_STATE),
            GmailOAuthCallbackParser.parse(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = GmailOAuthCallbackParser.HOST,
                status = "complete",
                code = "safe-code",
                state = null,
            ),
        )
        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_REQUEST),
            GmailOAuthCallbackParser.parse(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = GmailOAuthCallbackParser.HOST,
                status = "complete",
                code = "a".repeat(4_097),
                state = validState,
            ),
        )
        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_STATE),
            GmailOAuthCallbackParser.parse(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = GmailOAuthCallbackParser.HOST,
                status = "complete",
                code = "safe-code",
                state = "v1.short.not-valid",
            ),
        )
    }

    @Test
    fun `rejects duplicate callback query parameters instead of choosing one`() {
        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_REQUEST),
            GmailOAuthCallbackParser.parseParameters(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = GmailOAuthCallbackParser.HOST,
                statuses = listOf("complete"),
                codes = listOf("first-code", "second-code"),
                states = listOf(validState),
            ),
        )
        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_REQUEST),
            GmailOAuthCallbackParser.parseParameters(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = GmailOAuthCallbackParser.HOST,
                statuses = listOf("complete", "error"),
                codes = listOf("safe-code"),
                states = listOf(validState, validState),
            ),
        )
    }

    @Test
    fun `accepts legacy success callback without carrying email or tokens`() {
        val result = GmailOAuthCallbackParser.parse(
            scheme = "com.qolve.fluyo",
            host = "gmail-callback",
            status = "success",
            code = null,
        )

        assertSame(GmailOAuthCallback.Success, result)
    }

    @Test
    fun `maps allow-listed error code to a typed failure`() {
        val result = GmailOAuthCallbackParser.parse(
            scheme = GmailOAuthCallbackParser.SCHEME,
            host = GmailOAuthCallbackParser.HOST,
            status = "error",
            code = "state_expired",
        )

        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.STATE_EXPIRED),
            result,
        )
    }

    @Test
    fun `unknown server text is reduced to unknown and never retained`() {
        val result = GmailOAuthCallbackParser.parse(
            scheme = GmailOAuthCallbackParser.SCHEME,
            host = GmailOAuthCallbackParser.HOST,
            status = "error",
            code = "raw provider message with sensitive detail",
        )

        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.UNKNOWN),
            result,
        )
    }

    @Test
    fun `maps account conflict without retaining the raw callback code`() {
        val result = GmailOAuthCallbackParser.parse(
            scheme = GmailOAuthCallbackParser.SCHEME,
            host = GmailOAuthCallbackParser.HOST,
            status = "error",
            code = "account_conflict",
        )

        assertEquals(
            GmailOAuthCallback.Error(GmailOAuthCallbackError.ACCOUNT_CONFLICT),
            result,
        )
    }

    @Test
    fun `rejects callbacks for a different scheme or host`() {
        assertNull(
            GmailOAuthCallbackParser.parse(
                scheme = "https",
                host = GmailOAuthCallbackParser.HOST,
                status = "success",
                code = null,
            )
        )
        assertNull(
            GmailOAuthCallbackParser.parse(
                scheme = GmailOAuthCallbackParser.SCHEME,
                host = "attacker-callback",
                status = "success",
                code = null,
            )
        )
    }
}
