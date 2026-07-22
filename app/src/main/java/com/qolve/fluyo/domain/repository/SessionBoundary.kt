package com.qolve.fluyo.domain.repository

/**
 * Monotonic identity boundary used to reject work that completes after sign-out or an
 * account switch. [runIfCurrent] is atomic with the transition that advances the epoch.
 */
interface SessionBoundary {
    fun snapshot(): Long
    fun isCurrent(expectedEpoch: Long): Boolean
    fun runIfCurrent(expectedEpoch: Long, action: () -> Unit): Boolean
}
