package com.qolve.fluyo.presentation.util

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StableMutationRequestStoreTest {

    @Test
    fun `same payload and restored state reuse request id without storing plaintext`() {
        var sequence = 0
        val state = SavedStateHandle()
        val store = StableMutationRequestStore(state, "expense") { "request-${++sequence}" }

        val first = store.getOrCreate("1234", "category", "private description")
        val retry = StableMutationRequestStore(state, "expense") { "unexpected" }
            .getOrCreate("1234", "category", "private description")

        assertEquals(first, retry)
        assertEquals(1, sequence)
        assertFalse(state.keys().any { key ->
            state.get<Any?>(key)?.toString()?.contains("private description") == true
        })
    }

    @Test
    fun `changed payload cannot replace an uncertain request without reconciliation`() {
        var sequence = 0
        val store = StableMutationRequestStore(SavedStateHandle(), "goal") {
            "request-${++sequence}"
        }

        val first = store.getOrCreate("Laptop", "100000", null)
        assertEquals(first, store.existing("Laptop", "100000", null))
        assertEquals(null, store.existing("Laptop", "100001", null))
        assertThrows(IllegalStateException::class.java) {
            store.getOrCreate("Laptop", "100001", null)
        }
        assertEquals(first, store.pendingRequestId())
    }

    @Test
    fun `changed payload first reconciles a committed row`() = kotlinx.coroutines.test.runTest {
        val state = SavedStateHandle()
        val store = StableMutationRequestStore(state, "goal") { "request-1" }
        val first = store.getOrCreate("Laptop", "100000")

        val resolution = store.resolve(
            "Laptop", "120000",
            findCommitted = { requestId ->
                assertEquals(first, requestId)
                Result.success("already-created")
            },
        ).getOrThrow()

        assertEquals(
            PendingMutationResolution.Committed("already-created"),
            resolution,
        )
        assertEquals(null, store.pendingRequestId())
    }

    @Test
    fun `changed payload gets a new key only after confirmed absence`() =
        kotlinx.coroutines.test.runTest {
            var sequence = 0
            val store = StableMutationRequestStore(SavedStateHandle(), "goal") {
                "request-${++sequence}"
            }
            val first = store.getOrCreate("Laptop", "100000")

            val resolution = store.resolve(
                "Laptop", "120000",
                findCommitted = { requestId ->
                    assertEquals(first, requestId)
                    Result.success(null as String?)
                },
            ).getOrThrow()

            assertEquals(PendingMutationResolution.Ready("request-2"), resolution)
            assertEquals("request-2", store.existing("Laptop", "120000"))
        }

    @Test
    fun `lookup failure preserves pending key and fails closed`() =
        kotlinx.coroutines.test.runTest {
            val store = StableMutationRequestStore(SavedStateHandle(), "goal") { "request-1" }
            store.getOrCreate("Laptop", "100000")

            val result = store.resolve(
                "Laptop", "120000",
                findCommitted = { Result.failure<String?>(IllegalStateException("offline")) },
            )

            assertTrue(result.isFailure)
            assertEquals("request-1", store.pendingRequestId())
        }

    @Test
    fun `success releases key but stale completion cannot clear newer request`() {
        var sequence = 0
        val store = StableMutationRequestStore(SavedStateHandle(), "goal") {
            "request-${++sequence}"
        }

        val old = store.getOrCreate("Laptop", "100000")
        store.complete(old)
        val newer = store.getOrCreate("Laptop", "120000")
        store.complete(old)
        assertEquals(newer, store.getOrCreate("Laptop", "120000"))

        store.complete(newer)
        assertNotEquals(newer, store.getOrCreate("Laptop", "120000"))
    }

    @Test
    fun `fingerprint distinguishes component boundaries and null from empty`() {
        assertNotEquals(payloadFingerprint(listOf("ab", "c")), payloadFingerprint(listOf("a", "bc")))
        assertNotEquals(payloadFingerprint(listOf(null)), payloadFingerprint(listOf("")))
        assertEquals(64, payloadFingerprint(listOf("value")).length)
    }
}
