package com.qolve.fluyo.domain

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class SuspendRunCatchingTest {

    @Test
    fun `returns success after suspended work`() = runTest {
        val result = suspendRunCatching {
            yield()
            42
        }

        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `keeps ordinary failures in Result`() = runTest {
        val expected = IllegalStateException("boom")

        val result = suspendRunCatching<Int> {
            yield()
            throw expected
        }

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun `rethrows cancellation without wrapping it in Result`() = runTest {
        val expected = CancellationException("cancelled")

        try {
            suspendRunCatching<Unit> {
                yield()
                throw expected
            }
            throw AssertionError("CancellationException was not rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
