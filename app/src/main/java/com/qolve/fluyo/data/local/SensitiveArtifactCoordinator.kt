package com.qolve.fluyo.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes creation and session-boundary deletion of files that may contain financial data.
 *
 * Without this boundary, a slow export/import can recreate a previous user's artifact after
 * sign-out cleanup has already completed. Callers must revalidate their session from inside
 * [withLock] before creating anything.
 */
@Singleton
class SensitiveArtifactCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
