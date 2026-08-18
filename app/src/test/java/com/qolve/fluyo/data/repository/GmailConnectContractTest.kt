package com.qolve.fluyo.data.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailConnectContractTest {

    private val json = Json

    @Test
    fun `init request has the exact authenticated function shape`() {
        val encoded = json.encodeToString(
            GmailConnectContract.init("com.qolve.fluyo://gmail-callback"),
        )

        assertEquals(
            """{"action":"init","redirect_uri":"com.qolve.fluyo://gmail-callback"}""",
            encoded,
        )
    }

    @Test
    fun `completion request has the exact code and state shape`() {
        val encoded = json.encodeToString(
            GmailConnectContract.complete(
                authorizationCode = "safe-code",
                state = "v1.abcdefghijklmnop.abcdefghijklmnopqrstuvw",
            ),
        )

        assertEquals(
            """{"action":"complete","authorization_code":"safe-code","state":"v1.abcdefghijklmnop.abcdefghijklmnopqrstuvw"}""",
            encoded,
        )
    }
}
