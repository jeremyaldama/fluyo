package com.qolve.fluyo.data.mapper

import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.domain.model.NudgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMapperTest {

    @Test
    fun `explicitly empty notification list remains disabled`() {
        val user = UserDto(
            id = "user-1",
            authId = "auth-1",
            notificationTypes = emptyList(),
        ).toDomain()

        assertTrue(user.notificationTypes.isEmpty())
    }

    @Test
    fun `DTO default still enables every notification type`() {
        val user = UserDto(id = "user-1", authId = "auth-1").toDomain()

        assertEquals(NudgeType.entries.toSet(), user.notificationTypes)
    }
}
