package com.qolve.fluyo.data

import com.qolve.fluyo.domain.repository.SessionBoundary
import javax.inject.Inject
import javax.inject.Singleton

/** Process-local generation counter. All methods synchronize on the same monitor. */
@Singleton
class SessionEpoch @Inject constructor() : SessionBoundary {
    private var generation = 0L
    private var acceptingWork = true

    @Synchronized
    fun beginTransition(): Long {
        generation += 1L
        acceptingWork = false
        return generation
    }

    @Synchronized
    fun completeTransition() {
        acceptingWork = true
    }

    @Synchronized
    override fun snapshot(): Long = if (acceptingWork) generation else INVALID_EPOCH

    @Synchronized
    override fun isCurrent(expectedEpoch: Long): Boolean =
        acceptingWork && generation == expectedEpoch

    @Synchronized
    override fun runIfCurrent(expectedEpoch: Long, action: () -> Unit): Boolean {
        if (!acceptingWork || generation != expectedEpoch) return false
        action()
        return true
    }

    private companion object {
        const val INVALID_EPOCH = Long.MIN_VALUE
    }
}
