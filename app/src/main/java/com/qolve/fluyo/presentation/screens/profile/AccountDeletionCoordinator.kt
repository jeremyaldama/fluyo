package com.qolve.fluyo.presentation.screens.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Removes Fluyo's Gmail link before deleting the local account. */
internal object AccountDeletionCoordinator {
    suspend fun delete(
        disconnectGmail: suspend () -> Result<Int>,
        deleteAccount: suspend () -> Result<Unit>,
    ): Result<Unit> {
        try {
            disconnectGmail()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Gmail-link cleanup is best-effort; a service failure cannot trap the account.
        }
        currentCoroutineContext().ensureActive()
        return deleteAccount()
    }
}
