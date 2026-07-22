package com.qolve.fluyo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEpochTest {

    @Test
    fun `transition rejects old and in-transition publications`() {
        val boundary = SessionEpoch()
        val oldEpoch = boundary.snapshot()

        boundary.beginTransition()

        assertFalse(boundary.isCurrent(oldEpoch))
        assertFalse(boundary.runIfCurrent(oldEpoch) {})
        assertFalse(boundary.runIfCurrent(boundary.snapshot()) {})

        boundary.completeTransition()
        val currentEpoch = boundary.snapshot()

        assertTrue(boundary.isCurrent(currentEpoch))
        assertTrue(boundary.runIfCurrent(currentEpoch) {})
    }
}
