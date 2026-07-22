package com.qolve.fluyo.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostgrestPagingTest {

    @Test
    fun `collects full and partial pages without gaps`() = runTest {
        val requested = mutableListOf<LongRange>()
        val source = (0 until 12).toList()

        val result = collectPostgrestPages(pageSize = 5) { range ->
            requested += range
            source.slice(range.first.toInt() until minOf(range.last.toInt() + 1, source.size))
        }

        assertEquals(source, result)
        assertEquals(listOf(0L..4L, 5L..9L, 10L..14L), requested)
    }

    @Test
    fun `requests an empty terminator after an exact final page`() = runTest {
        val requested = mutableListOf<LongRange>()
        val source = (0 until 10).toList()

        val result = collectPostgrestPages(pageSize = 5) { range ->
            requested += range
            source.slice(range.first.toInt() until minOf(range.last.toInt() + 1, source.size))
        }

        assertEquals(source, result)
        assertEquals(listOf(0L..4L, 5L..9L, 10L..14L), requested)
    }

    @Test
    fun `rejects a response larger than the requested page`() = runTest {
        val error = runCatching {
            collectPostgrestPages(pageSize = 2) { listOf(1, 2, 3) }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `rejects invalid page sizes before querying`() = runTest {
        var queried = false

        val error = runCatching {
            collectPostgrestPages<Int>(pageSize = 0) {
                queried = true
                emptyList()
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(false, queried)
    }
}
