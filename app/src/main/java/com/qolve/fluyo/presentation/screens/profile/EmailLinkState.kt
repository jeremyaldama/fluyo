package com.qolve.fluyo.presentation.screens.profile

import com.qolve.fluyo.domain.model.EmailGrant
import com.qolve.fluyo.domain.repository.GmailAuthorizationException
import com.qolve.fluyo.domain.repository.GmailAuthorizationFailure
import com.qolve.fluyo.presentation.events.GmailOAuthCallbackError
import java.net.URI
import java.time.Instant

sealed interface EmailLinkState {
    data object Loading : EmailLinkState
    data object Disconnected : EmailLinkState
    data class Authorizing(val previousEmail: String? = null) : EmailLinkState
    data class Disconnecting(val email: String) : EmailLinkState
    data class Linked(val email: String) : EmailLinkState
    data class NeedsAttention(val email: String, val issue: EmailLinkIssue) : EmailLinkState
    data class Failed(val reason: EmailLinkFailure, val previousEmail: String? = null) : EmailLinkState
}

enum class EmailLinkIssue {
    WATCH_MISSING,
    WATCH_EXPIRED,
    WATCH_FAILED,
    AUTHORIZATION_EXPIRED,
    GMAIL_API_FAILED,
    WEBHOOK_FAILED,
    UNKNOWN,
}

enum class EmailLinkFailure {
    LOAD_FAILED,
    START_FAILED,
    INVALID_AUTHORIZATION_URL,
    BROWSER_UNAVAILABLE,
    OAUTH_DENIED,
    OAUTH_EXPIRED,
    OAUTH_SCOPE_MISSING,
    ACCOUNT_CONFLICT,
    WATCH_FAILED,
    SESSION_EXPIRED,
    SERVICE_UNAVAILABLE,
    OAUTH_FAILED,
    DISCONNECT_FAILED,
}

/** Converts only typed/whitelisted OAuth errors into user-facing failure categories. */
object EmailLinkFailureMapper {
    fun fromCallback(error: GmailOAuthCallbackError): EmailLinkFailure = when (error) {
        GmailOAuthCallbackError.ACCESS_DENIED -> EmailLinkFailure.OAUTH_DENIED
        GmailOAuthCallbackError.STATE_EXPIRED,
        GmailOAuthCallbackError.INVALID_STATE,
        GmailOAuthCallbackError.STATE_USER_MISMATCH -> EmailLinkFailure.OAUTH_EXPIRED
        GmailOAuthCallbackError.UNAUTHORIZED,
        GmailOAuthCallbackError.USER_NOT_FOUND -> EmailLinkFailure.SESSION_EXPIRED
        GmailOAuthCallbackError.MISSING_SCOPE -> EmailLinkFailure.OAUTH_SCOPE_MISSING
        GmailOAuthCallbackError.ACCOUNT_CONFLICT -> EmailLinkFailure.ACCOUNT_CONFLICT
        GmailOAuthCallbackError.WATCH_SETUP -> EmailLinkFailure.WATCH_FAILED
        GmailOAuthCallbackError.SERVER_NOT_CONFIGURED -> EmailLinkFailure.SERVICE_UNAVAILABLE
        else -> EmailLinkFailure.OAUTH_FAILED
    }

    fun fromCompletion(error: Throwable): EmailLinkFailure {
        val failure = (error as? GmailAuthorizationException)?.reason
            ?: return EmailLinkFailure.OAUTH_FAILED
        return when (failure) {
            GmailAuthorizationFailure.INVALID_STATE,
            GmailAuthorizationFailure.STATE_EXPIRED,
            GmailAuthorizationFailure.STATE_USER_MISMATCH -> EmailLinkFailure.OAUTH_EXPIRED
            GmailAuthorizationFailure.UNAUTHORIZED,
            GmailAuthorizationFailure.USER_NOT_FOUND -> EmailLinkFailure.SESSION_EXPIRED
            GmailAuthorizationFailure.MISSING_SCOPE -> EmailLinkFailure.OAUTH_SCOPE_MISSING
            GmailAuthorizationFailure.ACCOUNT_CONFLICT -> EmailLinkFailure.ACCOUNT_CONFLICT
            GmailAuthorizationFailure.WATCH_SETUP -> EmailLinkFailure.WATCH_FAILED
            GmailAuthorizationFailure.SERVER_NOT_CONFIGURED -> EmailLinkFailure.SERVICE_UNAVAILABLE
            else -> EmailLinkFailure.OAUTH_FAILED
        }
    }
}

/** Pure mapper kept separate from the ViewModel so health-state edge cases are testable. */
object EmailLinkStateResolver {
    fun resolve(grant: EmailGrant?, now: Instant): EmailLinkState {
        if (grant == null) return EmailLinkState.Disconnected
        if (grant.email.isBlank()) {
            return EmailLinkState.Failed(EmailLinkFailure.LOAD_FAILED)
        }

        grant.lastError?.let { code ->
            return EmailLinkState.NeedsAttention(
                email = grant.email,
                issue = issueForServerCode(code),
            )
        }

        val expiration = grant.watchExpiration
            ?: return EmailLinkState.NeedsAttention(grant.email, EmailLinkIssue.WATCH_MISSING)
        if (!expiration.isAfter(now)) {
            return EmailLinkState.NeedsAttention(grant.email, EmailLinkIssue.WATCH_EXPIRED)
        }
        return EmailLinkState.Linked(grant.email)
    }

    /**
     * Reconciles an uncertain completion response with public grant metadata. A stored grant
     * wins over a timeout/error because the server may have committed before the client failed.
     */
    fun resolveAfterCompletion(
        grant: EmailGrant?,
        now: Instant,
        missingFailure: EmailLinkFailure,
        previousEmail: String?,
    ): EmailLinkState = if (grant == null) {
        EmailLinkState.Failed(missingFailure, previousEmail)
    } else {
        resolve(grant, now)
    }

    fun issueForServerCode(code: String): EmailLinkIssue = when (code.trim().lowercase()) {
        "watch_failed" -> EmailLinkIssue.WATCH_FAILED
        "token_refresh_failed" -> EmailLinkIssue.AUTHORIZATION_EXPIRED
        "gmail_api_failed" -> EmailLinkIssue.GMAIL_API_FAILED
        "webhook_failed" -> EmailLinkIssue.WEBHOOK_FAILED
        else -> EmailLinkIssue.UNKNOWN
    }
}

/** Rejects a malformed or unexpected server response before handing it to ACTION_VIEW. */
object GmailAuthorizationUrlValidator {
    fun isAllowed(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("accounts.google.com", ignoreCase = true) &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.path == "/o/oauth2/v2/auth" &&
            uri.fragment == null
    }.getOrDefault(false)
}
