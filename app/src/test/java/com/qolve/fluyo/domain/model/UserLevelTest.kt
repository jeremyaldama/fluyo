package com.qolve.fluyo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserLevelTest {
    @Test
    fun `final level is reachable with the badge catalogue ceiling`() {
        assertEquals(4, UserLevelCatalog.levelFor(139).number)
        assertEquals(5, UserLevelCatalog.levelFor(140).number)
        assertEquals(5, UserLevelCatalog.levelFor(141).number)
    }
}
