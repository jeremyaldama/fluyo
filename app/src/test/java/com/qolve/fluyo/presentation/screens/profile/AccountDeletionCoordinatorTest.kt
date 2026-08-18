package com.qolve.fluyo.presentation.screens.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountDeletionCoordinatorTest {

    @Test
    fun `disconnects Gmail before deleting the account`() = runTest {
        val calls = mutableListOf<String>()

        val result = AccountDeletionCoordinator.delete(
            disconnectGmail = {
                calls += "disconnect"
                Result.success(1)
            },
            deleteAccount = {
                calls += "delete"
                Result.success(Unit)
            },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("disconnect", "delete"), calls)
    }

    @Test
    fun `Gmail cleanup failure does not block account deletion`() = runTest {
        var deleted = false

        val result = AccountDeletionCoordinator.delete(
            disconnectGmail = { Result.failure(IllegalStateException("offline")) },
            deleteAccount = {
                deleted = true
                Result.success(Unit)
            },
        )

        assertTrue(result.isSuccess)
        assertTrue(deleted)
    }

    @Test
    fun `cancellation stops before account deletion`() = runTest {
        var deleted = false

        try {
            AccountDeletionCoordinator.delete(
                disconnectGmail = { throw CancellationException("cancelled") },
                deleteAccount = {
                    deleted = true
                    Result.success(Unit)
                },
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation is control flow and must never be converted to best-effort.
        }

        assertFalse(deleted)
    }
}
