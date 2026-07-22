package com.qolve.fluyo.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCallbackPolicyTest {
    @Test
    fun `accepts a single non-empty PKCE authorization code`() {
        assertTrue(callback(codes = listOf("one-time-code")))
    }

    @Test
    fun `rejects implicit bearer tokens in fragment`() {
        assertFalse(
            callback(
                fragment = "access_token=secret&refresh_token=secret",
                names = emptySet(),
                codes = emptyList(),
            ),
        )
    }

    @Test
    fun `rejects duplicate blank and unexpected parameters`() {
        assertFalse(callback(codes = listOf("first", "second")))
        assertFalse(callback(codes = listOf("")))
        assertFalse(callback(names = setOf("code", "access_token")))
    }

    @Test
    fun `rejects a callback for another authority or path`() {
        assertFalse(callback(host = "attacker"))
        assertFalse(callback(path = "/other"))
        assertFalse(callback(userInfo = "user"))
    }

    @Test
    fun `accepts a provider error without tokens`() {
        assertTrue(
            callback(
                names = setOf("error", "error_description"),
                codes = emptyList(),
                error = "access_denied",
            ),
        )
    }

    private fun callback(
        scheme: String? = AuthCallbackPolicy.EXPECTED_SCHEME,
        host: String? = AuthCallbackPolicy.EXPECTED_HOST,
        userInfo: String? = null,
        port: Int = -1,
        path: String? = null,
        fragment: String? = null,
        names: Set<String> = setOf("code"),
        codes: List<String> = listOf("code"),
        error: String? = null,
    ): Boolean = AuthCallbackPolicy.accepts(
        scheme = scheme,
        host = host,
        userInfo = userInfo,
        port = port,
        path = path,
        fragment = fragment,
        queryParameterNames = names,
        authorizationCodes = codes,
        error = error,
    )
}
