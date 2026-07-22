package com.qolve.fluyo.data.security

/**
 * Fail-closed policy for the temporary custom-scheme PKCE callback.
 *
 * A verified HTTPS App Link remains the preferred production destination. Until the
 * deployment owns a domain, this policy makes the custom scheme code-only and rejects
 * implicit-flow bearer tokens, fragments, extra parameters and malformed callbacks.
 */
object AuthCallbackPolicy {
    private val errorParameters = setOf("error", "error_code", "error_description")

    fun accepts(
        scheme: String?,
        host: String?,
        userInfo: String?,
        port: Int,
        path: String?,
        fragment: String?,
        queryParameterNames: Set<String>,
        authorizationCodes: List<String>,
        error: String?,
    ): Boolean {
        if (!scheme.equals(EXPECTED_SCHEME, ignoreCase = true)) return false
        if (!host.equals(EXPECTED_HOST, ignoreCase = true)) return false
        if (userInfo != null || port != -1) return false
        if (!path.isNullOrEmpty() && path != "/") return false
        if (fragment != null) return false

        val isAuthorizationCode =
            queryParameterNames == setOf("code") &&
                authorizationCodes.size == 1 &&
                authorizationCodes.single().isNotBlank()
        val isProviderError =
            queryParameterNames.isNotEmpty() &&
                queryParameterNames.all(errorParameters::contains) &&
                !error.isNullOrBlank()
        return isAuthorizationCode || isProviderError
    }

    const val EXPECTED_SCHEME = "fluyo"
    const val EXPECTED_HOST = "auth-callback"
}
