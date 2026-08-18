package com.qolve.fluyo.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire models for the authenticated gmail-connect Edge Function. */
internal object GmailConnectContract {
    const val FUNCTION_NAME = "gmail-connect"

    fun init(redirectUri: String) = InitRequest(
        action = "init",
        redirectUri = redirectUri,
    )

    fun complete(authorizationCode: String, state: String) = CompleteRequest(
        action = "complete",
        authorizationCode = authorizationCode,
        state = state,
    )

    @Serializable
    data class InitRequest(
        val action: String,
        @SerialName("redirect_uri") val redirectUri: String,
    )

    @Serializable
    data class CompleteRequest(
        val action: String,
        @SerialName("authorization_code") val authorizationCode: String,
        val state: String,
    )

    @Serializable
    data class InitResponse(
        @SerialName("authorization_url") val authorizationUrl: String,
        @SerialName("expires_in") val expiresIn: Int? = null,
    )

    @Serializable
    data class CompleteResponse(val status: String)

    @Serializable
    data class ErrorResponse(val error: String)
}
