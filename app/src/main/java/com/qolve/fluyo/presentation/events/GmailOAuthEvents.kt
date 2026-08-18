package com.qolve.fluyo.presentation.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GmailOAuthCallback {
    data class Complete(
        val authorizationCode: String,
        val state: String,
    ) : GmailOAuthCallback

    data object Success : GmailOAuthCallback
    data class Error(val reason: GmailOAuthCallbackError) : GmailOAuthCallback
}

enum class GmailOAuthCallbackError {
    INVALID_REQUEST,
    ACCESS_DENIED,
    STATE_EXPIRED,
    INVALID_STATE,
    STATE_USER_MISMATCH,
    UNAUTHORIZED,
    USER_NOT_FOUND,
    TOKEN_EXCHANGE,
    MISSING_SCOPE,
    PROFILE_LOOKUP,
    WATCH_SETUP,
    ACCOUNT_CONFLICT,
    GRANT_STORAGE,
    SERVER_NOT_CONFIGURED,
    SERVER,
    UNKNOWN,
}

/** Parses only Fluyo's exact callback target and discards untrusted raw error text. */
object GmailOAuthCallbackParser {
    const val SCHEME = "com.qolve.fluyo"
    const val HOST = "gmail-callback"
    const val REDIRECT_URI = "$SCHEME://$HOST"

    fun parseParameters(
        scheme: String?,
        host: String?,
        statuses: List<String>,
        codes: List<String>,
        states: List<String>,
    ): GmailOAuthCallback? {
        if (!isCallbackTarget(scheme, host)) return null
        if (statuses.size != 1 || codes.size > 1 || states.size > 1) {
            return GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_REQUEST)
        }
        return parse(
            scheme = scheme,
            host = host,
            status = statuses.single(),
            code = codes.singleOrNull(),
            state = states.singleOrNull(),
        )
    }

    fun parse(
        scheme: String?,
        host: String?,
        status: String?,
        code: String?,
        state: String? = null,
    ): GmailOAuthCallback? {
        if (!isCallbackTarget(scheme, host)) return null

        return when (status) {
            "complete" -> when {
                code == null || !authorizationCodePattern.matches(code) ->
                    GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_REQUEST)
                state == null || !opaqueStatePattern.matches(state) ->
                    GmailOAuthCallback.Error(GmailOAuthCallbackError.INVALID_STATE)
                else -> GmailOAuthCallback.Complete(code, state)
            }
            "success" -> GmailOAuthCallback.Success
            "error" -> GmailOAuthCallback.Error(errorForCode(code))
            else -> GmailOAuthCallback.Error(GmailOAuthCallbackError.UNKNOWN)
        }
    }

    fun errorForCode(code: String?): GmailOAuthCallbackError = when (code?.trim()?.lowercase()) {
        "invalid_request" -> GmailOAuthCallbackError.INVALID_REQUEST
        "access_denied" -> GmailOAuthCallbackError.ACCESS_DENIED
        "state_expired" -> GmailOAuthCallbackError.STATE_EXPIRED
        "invalid_state", "missing_state" -> GmailOAuthCallbackError.INVALID_STATE
        "state_user_mismatch" -> GmailOAuthCallbackError.STATE_USER_MISMATCH
        "unauthorized" -> GmailOAuthCallbackError.UNAUTHORIZED
        "user_not_found" -> GmailOAuthCallbackError.USER_NOT_FOUND
        "token_exchange_failed", "oauth_exchange_failed", "missing_refresh_token" ->
            GmailOAuthCallbackError.TOKEN_EXCHANGE
        "missing_scope" -> GmailOAuthCallbackError.MISSING_SCOPE
        "profile_failed" -> GmailOAuthCallbackError.PROFILE_LOOKUP
        "watch_failed" -> GmailOAuthCallbackError.WATCH_SETUP
        "account_conflict" -> GmailOAuthCallbackError.ACCOUNT_CONFLICT
        "grant_store_failed" -> GmailOAuthCallbackError.GRANT_STORAGE
        "server_not_configured" -> GmailOAuthCallbackError.SERVER_NOT_CONFIGURED
        "server_error" -> GmailOAuthCallbackError.SERVER
        else -> GmailOAuthCallbackError.UNKNOWN
    }

    // OAuth authorization codes are opaque. Accept bounded printable ASCII so a future Google
    // format remains compatible while excluding spaces, newlines, NUL and other controls.
    private val authorizationCodePattern = Regex("^[\\x21-\\x7E]{1,4096}$")
    private val opaqueStatePattern = Regex("^v1\\.[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{23,4076}$")

    private fun isCallbackTarget(scheme: String?, host: String?): Boolean =
        scheme.equals(SCHEME, ignoreCase = false) && host.equals(HOST, ignoreCase = false)
}

/** Buffered callback bridge between MainActivity and the profile ViewModel. */
@Singleton
class GmailOAuthEvents @Inject constructor() {
    private val mutableEvents = MutableSharedFlow<GmailOAuthCallback>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GmailOAuthCallback> = mutableEvents.asSharedFlow()

    fun emit(callback: GmailOAuthCallback) {
        mutableEvents.tryEmit(callback)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consume() {
        mutableEvents.resetReplayCache()
    }
}
