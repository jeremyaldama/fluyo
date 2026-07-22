package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.WhatsAppLinkChallengeDto
import com.qolve.fluyo.data.dto.WhatsAppLinkDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class WhatsAppLinkMapperTest {

    @Test
    fun `challenge DTO maps expiry and builds exact backend message`() {
        val challengeValue = ("01234567" + "89abcdef").repeat(2)
        val dto = Json.decodeFromString<WhatsAppLinkChallengeDto>(
            """{"token":"$challengeValue","expires_at":"2026-07-22T18:10:00Z"}""",
        )

        val challenge = dto.toDomain()

        assertEquals(Instant.parse("2026-07-22T18:10:00Z"), challenge.expiresAt)
        assertEquals("VINCULAR FLUYO $challengeValue", challenge.message)
        assertFalse(dto.toString().contains(challengeValue))
        assertFalse(challenge.toString().contains(challengeValue))
    }

    @Test
    fun `verified link maps canonical backend-owned sender`() {
        val dto = WhatsAppLinkDto(
            userId = "user-id",
            phoneE164 = "+51987654321",
            verifiedAt = "2026-07-22T18:11:12+00:00",
        )

        val link = dto.toDomain()

        assertEquals("+51987654321", link.phoneE164)
        assertEquals(Instant.parse("2026-07-22T18:11:12Z"), link.verifiedAt)
        assertEquals("•••• 4321", link.maskedPhone)
        assertFalse(link.toString().contains(link.phoneE164))
    }

    @Test
    fun `invalid challenge token fails without echoing token`() {
        val invalid = "not-a-token"

        val error = runCatching {
            WhatsAppLinkChallengeDto(
                token = invalid,
                expiresAt = "2026-07-22T18:10:00Z",
            ).toDomain()
        }.exceptionOrNull()

        requireNotNull(error)
        assertFalse(error.message.orEmpty().contains(invalid))
    }
}
