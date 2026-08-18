package com.qolve.fluyo.presentation.screens.profile

import com.qolve.fluyo.domain.model.EmailGrant
import com.qolve.fluyo.domain.repository.GmailAuthorizationException
import com.qolve.fluyo.domain.repository.GmailAuthorizationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EmailLinkStateResolverTest {

    private val now = Instant.parse("2026-08-17T12:00:00Z")

    @Test
    fun `null grant is disconnected`() {
        assertSame(EmailLinkState.Disconnected, EmailLinkStateResolver.resolve(null, now))
    }

    @Test
    fun `healthy future watch is linked`() {
        val state = EmailLinkStateResolver.resolve(
            EmailGrant(
                email = "user@example.com",
                watchExpiration = now.plusSeconds(3_600),
                lastError = null,
            ),
            now,
        )

        assertEquals(EmailLinkState.Linked("user@example.com"), state)
    }

    @Test
    fun `missing and expired watch need attention`() {
        assertEquals(
            EmailLinkState.NeedsAttention("user@example.com", EmailLinkIssue.WATCH_MISSING),
            EmailLinkStateResolver.resolve(
                EmailGrant("user@example.com", watchExpiration = null, lastError = null),
                now,
            ),
        )
        assertEquals(
            EmailLinkState.NeedsAttention("user@example.com", EmailLinkIssue.WATCH_EXPIRED),
            EmailLinkStateResolver.resolve(
                EmailGrant("user@example.com", watchExpiration = now, lastError = null),
                now,
            ),
        )
    }

    @Test
    fun `server codes are mapped through a strict whitelist`() {
        assertEquals(
            EmailLinkIssue.AUTHORIZATION_EXPIRED,
            EmailLinkStateResolver.issueForServerCode("token_refresh_failed"),
        )
        assertEquals(
            EmailLinkIssue.UNKNOWN,
            EmailLinkStateResolver.issueForServerCode("provider said token=secret"),
        )
    }

    @Test
    fun `only canonical Google consent urls are opened`() {
        assertTrue(
            GmailAuthorizationUrlValidator.isAllowed(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&state=opaque",
            )
        )
        assertFalse(
            GmailAuthorizationUrlValidator.isAllowed(
                "https://accounts.google.com.attacker.example/o/oauth2/v2/auth",
            )
        )
        assertFalse(
            GmailAuthorizationUrlValidator.isAllowed(
                "http://accounts.google.com/o/oauth2/v2/auth",
            )
        )
        assertFalse(GmailAuthorizationUrlValidator.isAllowed("not a url"))
    }

    @Test
    fun `completion failures are reduced to safe user facing categories`() {
        assertEquals(
            EmailLinkFailure.OAUTH_EXPIRED,
            EmailLinkFailureMapper.fromCompletion(
                GmailAuthorizationException(GmailAuthorizationFailure.STATE_USER_MISMATCH),
            ),
        )
        assertEquals(
            EmailLinkFailure.SESSION_EXPIRED,
            EmailLinkFailureMapper.fromCompletion(
                GmailAuthorizationException(GmailAuthorizationFailure.UNAUTHORIZED),
            ),
        )
        assertEquals(
            EmailLinkFailure.SERVICE_UNAVAILABLE,
            EmailLinkFailureMapper.fromCompletion(
                GmailAuthorizationException(GmailAuthorizationFailure.SERVER_NOT_CONFIGURED),
            ),
        )
        assertEquals(
            EmailLinkFailure.OAUTH_FAILED,
            EmailLinkFailureMapper.fromCompletion(IllegalStateException("raw provider detail")),
        )
    }

    @Test
    fun `stored grant wins when completion response was uncertain`() {
        val grant = EmailGrant(
            email = "linked@example.com",
            watchExpiration = now.plusSeconds(3_600),
            lastError = null,
        )

        assertEquals(
            EmailLinkState.Linked("linked@example.com"),
            EmailLinkStateResolver.resolveAfterCompletion(
                grant = grant,
                now = now,
                missingFailure = EmailLinkFailure.OAUTH_FAILED,
                previousEmail = null,
            ),
        )
        assertEquals(
            EmailLinkState.Failed(
                reason = EmailLinkFailure.OAUTH_EXPIRED,
                previousEmail = "old@example.com",
            ),
            EmailLinkStateResolver.resolveAfterCompletion(
                grant = null,
                now = now,
                missingFailure = EmailLinkFailure.OAUTH_EXPIRED,
                previousEmail = "old@example.com",
            ),
        )
    }
}
